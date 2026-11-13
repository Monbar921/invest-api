package ru.invest.api.portfolio.service.usecase.impl;

import ru.invest.api.portfolio.service.model.IncomeModel;
import ru.invest.api.portfolio.service.usecase.impl.BondIncome.Flow;
import ru.invest.api.portfolio.service.usecase.impl.BondIncome.FlowKind;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Полный доход одной облигации или нескольких (итог валюты, весь портфель) из их денежных потоков.
 */
public final class IncomeAggregator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final int SCALE = 10;
    private static final String RUB = "rub";
    /**
     * XIRR - только если от первого потока до последнего не меньше 30 дней: на коротком сроке годовая ставка
     * раздувается до тысяч процентов (+1 % за неделю - это ~68 % годовых) и пугает больше, чем сообщает
     */
    private static final int MIN_XIRR_DAYS = 30;

    private IncomeAggregator() {
    }

    /**
     * @param currency валюта итога; в рублях (inRub) - rub
     * @param incomes  облигации итога, все в валюте currency (кроме рублёвого итога)
     * @param inRub    суммы в рублях - по курсу ЦБ на дату каждого потока
     * @param taxRate  ставка НДФЛ, %; null - налог не считается
     * @return null - ни одну облигацию не посчитать (их нет в системе или нет курса)
     */
    public static IncomeModel of(final String currency, final List<BondIncome> incomes, final boolean inRub,
                                 final BigDecimal taxRate) {
        final List<BondIncome> usable = incomes.stream()
                .filter(BondIncome::bondKnown)
                .filter(income -> !inRub || income.rubKnown())
                .toList();
        if (usable.isEmpty()) {
            return null;
        }
        final List<BondIncome> valued = usable.stream().filter(BondIncome::valueKnown).toList();
        final boolean hasValue = !valued.isEmpty();
        // облигация без текущей цены в итог не входит; одна такая - показываются суммы без её стоимости
        final List<BondIncome> base = hasValue ? valued : usable;
        final Function<Flow, BigDecimal> amount = inRub ? Flow::amountInRub : Flow::amount;

        final BigDecimal buyAmount = sum(base, amount, EnumSet.of(FlowKind.BUY));
        final BigDecimal buyAci = sum(base, amount, EnumSet.of(FlowKind.BUY_ACI));
        final BigDecimal coupons = sum(base, amount, EnumSet.of(FlowKind.COUPON));
        final BigDecimal amortization = sum(base, amount, EnumSet.of(FlowKind.AMORTIZATION));
        final BigDecimal soldAmount = sum(base, amount, EnumSet.of(FlowKind.SALE, FlowKind.REDEMPTION));
        final BigDecimal saleAci = sum(base, amount, EnumSet.of(FlowKind.SALE_ACI));
        final BigDecimal currentAmount = sum(base, amount, EnumSet.of(FlowKind.VALUE));
        final BigDecimal currentAci = sum(base, amount, EnumSet.of(FlowKind.VALUE_ACI));
        final BigDecimal commission = sum(base, amount, EnumSet.of(FlowKind.BUY_COMMISSION, FlowKind.SALE_COMMISSION));
        final BigDecimal aciIncome = saleAci.add(currentAci).subtract(buyAci);
        final int unknownCoupons = base.stream().mapToInt(BondIncome::unknownCouponCount).sum();
        final boolean incomplete = usable.size() < incomes.size() || valued.size() < usable.size()
                || unknownCoupons > 0 || base.stream().anyMatch(BondIncome::incomplete);

        BigDecimal priceIncome = null;
        BigDecimal profit = null;
        BigDecimal tax = null;
        BigDecimal fullIncome = null;
        BigDecimal fullIncomePercent = null;
        BigDecimal xirr = null;
        if (hasValue) {
            profit = soldAmount.add(currentAmount).add(amortization).subtract(buyAmount)
                    .add(coupons).add(aciIncome).subtract(commission);
            // налог - с каждой облигации отдельно: убыток по одной не уменьшает налог по другой
            tax = taxRate == null
                    ? BigDecimal.ZERO
                    : base.stream()
                            .map(income -> signedSum(income.flows(), amount).max(BigDecimal.ZERO))
                            .map(income -> income.multiply(taxRate).divide(HUNDRED, SCALE, RoundingMode.HALF_UP))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            fullIncome = money(profit.subtract(tax));
            profit = money(profit);
            tax = money(tax);
            // части в копейках складываются ровно в полный доход: копейка округления остаётся в доходе от цены
            priceIncome = fullIncome.add(tax).add(money(commission)).subtract(money(coupons)).subtract(money(aciIncome));
            final BigDecimal invested = buyAmount.add(buyAci);
            fullIncomePercent = invested.signum() == 0
                    ? null
                    : fullIncome.multiply(HUNDRED).divide(invested, PERCENT_SCALE, RoundingMode.HALF_UP);
            final List<Xirr.CashFlow> flows = base.stream()
                    .flatMap(income -> income.flows().stream())
                    .map(flow -> new Xirr.CashFlow(flow.date(), flow.kind().signed(amount.apply(flow))))
                    .toList();
            xirr = incomplete || spanDays(flows) < MIN_XIRR_DAYS ? null : Xirr.percent(flows);
        }
        return new IncomeModel(inRub ? RUB : currency, money(buyAmount), money(buyAci), money(coupons),
                money(amortization), money(soldAmount), money(saleAci), hasValue ? money(currentAmount) : null,
                money(currentAci), money(commission), money(priceIncome), money(aciIncome), money(profit), taxRate,
                money(tax), money(fullIncome), fullIncomePercent, xirr, unknownCoupons, incomplete);
    }

    /** Дней от первого потока до последнего */
    private static long spanDays(final List<Xirr.CashFlow> flows) {
        final LocalDate first = flows.stream().map(Xirr.CashFlow::date).min(Comparator.naturalOrder()).orElse(null);
        final LocalDate last = flows.stream().map(Xirr.CashFlow::date).max(Comparator.naturalOrder()).orElse(null);
        return first == null ? 0 : ChronoUnit.DAYS.between(first, last);
    }

    private static BigDecimal sum(final List<BondIncome> incomes, final Function<Flow, BigDecimal> amount,
                                  final Set<FlowKind> kinds) {
        return incomes.stream()
                .flatMap(income -> income.flows().stream())
                .filter(flow -> kinds.contains(flow.kind()))
                .map(amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Доход до налога одной облигации - сумма её потоков со знаком */
    private static BigDecimal signedSum(final List<Flow> flows, final Function<Flow, BigDecimal> amount) {
        return flows.stream()
                .map(flow -> flow.kind().signed(amount.apply(flow)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(final BigDecimal amount) {
        return amount == null ? null : amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
