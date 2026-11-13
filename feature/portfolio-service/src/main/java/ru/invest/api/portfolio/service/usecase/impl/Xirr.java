package ru.invest.api.portfolio.service.usecase.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * XIRR - годовая ставка, при которой сумма денежных потоков, дисконтированных к дате первого, равна нулю:
 * {@code Σ сумма / (1 + r) ^ (дней от первого потока / 365) = 0}. Так же считает XIRR в Excel.
 */
public final class Xirr {
    private static final double DAYS_IN_YEAR = 365.0;
    private static final double MIN_RATE = -0.999_999;
    private static final double MAX_RATE = 1_000_000.0;
    private static final double TOLERANCE = 1e-10;
    private static final int MAX_ITERATIONS = 300;
    private static final int PERCENT_SCALE = 2;
    private static final int HUNDRED = 100;

    private Xirr() {
    }

    /**
     * @param flows потоки: вложения - отрицательные, полученное - положительные
     * @return % годовых; null - посчитать нельзя: все потоки в один день, нет и вложений, и получения, или у
     *         уравнения нет корня
     */
    public static BigDecimal percent(final List<CashFlow> flows) {
        if (flows.stream().noneMatch(flow -> flow.amount().signum() < 0)
                || flows.stream().noneMatch(flow -> flow.amount().signum() > 0)) {
            return null;
        }
        final LocalDate first = flows.stream().map(CashFlow::date).min(Comparator.naturalOrder()).orElseThrow();
        final LocalDate last = flows.stream().map(CashFlow::date).max(Comparator.naturalOrder()).orElseThrow();
        if (!last.isAfter(first)) {
            return null;
        }
        final double[] amounts = flows.stream().mapToDouble(flow -> flow.amount().doubleValue()).toArray();
        final double[] years = flows.stream()
                .mapToDouble(flow -> ChronoUnit.DAYS.between(first, flow.date()) / DAYS_IN_YEAR)
                .toArray();

        // корень ищется делением отрезка: надёжнее метода Ньютона на коротких сроках, где ставка бывает огромной
        double low = MIN_RATE;
        double high = MAX_RATE;
        double fLow = npv(amounts, years, low);
        final double fHigh = npv(amounts, years, high);
        if (Double.isNaN(fLow) || Double.isNaN(fHigh) || Math.signum(fLow) == Math.signum(fHigh)) {
            return null;
        }
        for (int i = 0; i < MAX_ITERATIONS && high - low > TOLERANCE * Math.max(1, Math.abs(low)); i++) {
            final double middle = low + (high - low) / 2;
            final double fMiddle = npv(amounts, years, middle);
            if (fMiddle == 0) {
                low = middle;
                high = middle;
                break;
            }
            if (Math.signum(fMiddle) == Math.signum(fLow)) {
                low = middle;
                fLow = fMiddle;
            } else {
                high = middle;
            }
        }
        return BigDecimal.valueOf((low + high) / 2 * HUNDRED).setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static double npv(final double[] amounts, final double[] years, final double rate) {
        // через логарифм: (1 + r) ^ t при больших r и t переполняет double
        final double logBase = Math.log1p(rate);
        double sum = 0;
        for (int i = 0; i < amounts.length; i++) {
            sum += amounts[i] * Math.exp(-years[i] * logBase);
        }
        return sum;
    }

    /** Денежный поток: дата и сумма со знаком */
    public record CashFlow(LocalDate date, BigDecimal amount) {
    }
}
