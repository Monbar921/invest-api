package ru.invest.api.news.supplier.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Кривая бескупонной доходности ОФЗ Мосбиржи (G-curve) по её параметрам - методика Банка России и Мосбиржи:
 * <pre>
 * G(t) = B1 + (B2 + B3) × T1 / t × (1 − e^(−t / T1)) − B3 × e^(−t / T1) + Σ Gi × e^(−(t − ai)² / bi²),  i = 1..9
 * a1 = 0, a2 = 0,6, a(i+1) = ai + 0,6 × k^(i−1);  b1 = 0,6, b(i+1) = bi × k;  k = 1,6
 * Y(t) = (e^(G(t) / 10000) − 1) × 100
 * </pre>
 * G - непрерывно начисляемая доходность в базисных пунктах, Y - эффективная годовая в процентах (так же считается
 * доходность к погашению облигации). Сверено с доходностями на стандартные сроки, которые публикует биржа.
 */
public final class OfzYieldCurve {
    /** На сроки дольше биржа кривую не публикует - дальше это экстраполяция */
    public static final double MAX_TERM_YEARS = 30;

    private static final int TERMS = 9;
    private static final double K = 1.6;
    private static final double FIRST_STEP = 0.6;
    private static final double BASIS_POINTS = 10_000;
    private static final int YIELD_SCALE = 4;
    private static final double[] A = new double[TERMS];
    private static final double[] B = new double[TERMS];

    static {
        A[0] = 0;
        A[1] = FIRST_STEP;
        for (int i = 2; i < TERMS; i++) {
            A[i] = A[i - 1] + FIRST_STEP * Math.pow(K, i - 1);
        }
        B[0] = FIRST_STEP;
        for (int i = 1; i < TERMS; i++) {
            B[i] = B[i - 1] * K;
        }
    }

    private final LocalDate tradeDate;
    private final double b1;
    private final double b2;
    private final double b3;
    private final double t1;
    private final double[] g;

    public OfzYieldCurve(final LocalDate tradeDate, final double b1, final double b2, final double b3, final double t1,
                         final double[] g) {
        if (g.length != TERMS || t1 <= 0) {
            throw new IllegalArgumentException("G-curve needs 9 G parameters and positive T1");
        }
        this.tradeDate = tradeDate;
        this.b1 = b1;
        this.b2 = b2;
        this.b3 = b3;
        this.t1 = t1;
        this.g = g.clone();
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    /**
     * Доходность ОФЗ на срок, % годовых (эффективная); срок не больше 0 или больше 30 лет - пусто.
     */
    public Optional<BigDecimal> yieldAt(final double years) {
        if (!(years > 0) || years > MAX_TERM_YEARS) {
            return Optional.empty();
        }
        final double decay = Math.exp(-years / t1);
        double basisPoints = b1 + (b2 + b3) * t1 / years * (1 - decay) - b3 * decay;
        for (int i = 0; i < TERMS; i++) {
            basisPoints += g[i] * Math.exp(-Math.pow(years - A[i], 2) / Math.pow(B[i], 2));
        }
        final double percent = (Math.exp(basisPoints / BASIS_POINTS) - 1) * 100;
        return Double.isFinite(percent)
                ? Optional.of(BigDecimal.valueOf(percent).setScale(YIELD_SCALE, RoundingMode.HALF_UP))
                : Optional.empty();
    }
}
