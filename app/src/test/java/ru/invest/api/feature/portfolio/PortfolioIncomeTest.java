package ru.invest.api.feature.portfolio;

import org.junit.jupiter.api.Test;
import ru.invest.api.common.entity.PortfolioPosition;
import ru.invest.api.common.entity.PortfolioSale;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.calculation.ExitType;
import ru.invest.api.common.model.calculation.ProfitCalculationModel;
import ru.invest.api.common.model.calculation.ProfitCalculationRequestModel;
import ru.invest.api.currency.service.usecase.CurrencyPriceUseCase;
import ru.invest.api.portfolio.service.model.IncomeModel;
import ru.invest.api.portfolio.service.model.TradeModel;
import ru.invest.api.portfolio.service.model.TradeType;
import ru.invest.api.portfolio.service.usecase.impl.BondIncome;
import ru.invest.api.portfolio.service.usecase.impl.IncomeAggregator;
import ru.invest.api.portfolio.service.usecase.impl.PortfolioIncomeCalculator;
import ru.invest.api.portfolio.service.usecase.impl.RubRates;
import ru.invest.api.portfolio.service.usecase.impl.Xirr;
import ru.invest.api.tinkoff.supplier.service.PriceConversionService;
import ru.invest.api.tinkoff.supplier.service.impl.ProfitCalculationServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Полный доход облигации портфеля без Spring и БД: сравнение с калькулятором дохода и ручным расчётом, FIFO частичных
 * продаж, погашение, неполные итоги и XIRR.
 */
public class PortfolioIncomeTest {
    private static final BigDecimal TAX_13 = new BigDecimal("13");
    private static final LocalDate DAY_0 = LocalDate.of(2026, 1, 1);

    /**
     * Готово, когда: полный доход совпадает с калькулятором дохода за тот же период. Калькулятор считает в день
     * покупки, портфель - позже, когда облигация продана и часть номинала уже погашена амортизацией.
     */
    @Test
    void matchesProfitCalculatorForTheSamePeriod() {
        final LocalDate sellDate = DAY_0.plusDays(250);
        // в день покупки: номинал 1000, впереди амортизация 300 на 223-й день
        final BondModel atBuy = bond("1000", "98", "4.80", DAY_0.plusDays(400),
                List.of(coupon(-50, "35"), coupon(41, "35"), coupon(132, "35"), coupon(223, "30"), coupon(314, "25")),
                List.of(amortization(223, "300")));
        final ProfitCalculationModel calculated = calculator(DAY_0).calculate(atBuy, new ProfitCalculationRequestModel()
                .setBuyDate(DAY_0)
                .setSellDate(sellDate)
                .setQuantity(10)
                .setBuyPricePercent(new BigDecimal("98"))
                .setSellPricePercent(new BigDecimal("101"))
                .setTaxRatePercent(TAX_13));
        assertThat(calculated.getExitType(), equalTo(ExitType.SALE));

        // через 300 дней: номинал уже 700, амортизация в прошлом
        final LocalDate later = DAY_0.plusDays(300);
        final BondModel now = bond("700", "99", "1.00", DAY_0.plusDays(400),
                atBuy.getCoupon().getCouponData(), atBuy.getAmortizations());
        // НКД при покупке - как в отчёте брокера (калькулятор взял его из T-Invest на день покупки); при продаже не
        // введён - считается по графику, как и в калькуляторе
        final BondIncome income = income(later, now, List.of(lot(1L, DAY_0, 10, "98", calculated.getPurchaseAci(),
                null)), List.of(sale(1L, sellDate, 10, "101", null, null)));
        final IncomeModel model = IncomeAggregator.of("rub", List.of(income), false, TAX_13);

        assertThat(income.openQuantity(), equalTo(0));
        assertThat(model.buyAmount(), comparesEqualTo(calculated.getPurchaseAmount()));
        assertThat(model.couponIncome(), comparesEqualTo(calculated.getCouponIncome()));
        assertThat(model.amortizationIncome(), comparesEqualTo(calculated.getAmortizationIncome()));
        assertThat(model.soldAmount(), comparesEqualTo(calculated.getExitAmount()));
        assertThat(model.saleAci(), comparesEqualTo(calculated.getExitAci()));
        assertThat(model.profit(), comparesEqualTo(calculated.getProfit()));
        assertThat(model.tax(), comparesEqualTo(calculated.getTax()));
        assertThat(model.fullIncome(), comparesEqualTo(calculated.getNetProfit()));
        assertThat(model.fullIncomePercent(), comparesEqualTo(calculated.getNetProfitPercent()));
        assertThat(model.incomplete(), equalTo(false));
    }

    /**
     * Ручной расчёт: 10 облигаций по 95% номинала 1000, два купона по 50, продажа по 100% в день купона (НКД 0),
     * комиссия 10 за покупку и 10 за продажу, НДФЛ 13%.
     */
    @Test
    void matchesManualCalculation() {
        final BondModel bond = bond("1000", "100", "0", DAY_0.plusYears(3),
                List.of(couponOn(DAY_0, "50"), couponOn(DAY_0.plusDays(182), "50"),
                        couponOn(DAY_0.plusDays(364), "50"), couponOn(DAY_0.plusDays(546), "50")),
                List.of());
        final BondIncome income = income(DAY_0.plusDays(400), bond,
                List.of(lot(1L, DAY_0, 10, "95", null, "10")),
                List.of(sale(1L, DAY_0.plusDays(364), 10, "100", null, "10")));
        final IncomeModel model = IncomeAggregator.of("rub", List.of(income), false, TAX_13);

        // купон в день покупки - продавцу, в день продажи - нам
        assertThat(model.couponIncome(), comparesEqualTo(new BigDecimal("1000")));
        assertThat(model.buyAci(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(model.saleAci(), comparesEqualTo(BigDecimal.ZERO));
        // 10 × 1000 × (100 − 95)%
        assertThat(model.priceIncome(), comparesEqualTo(new BigDecimal("500")));
        assertThat(model.commission(), comparesEqualTo(new BigDecimal("20")));
        // 500 + 1000 − 20 = 1480, налог 13% = 192,40
        assertThat(model.profit(), comparesEqualTo(new BigDecimal("1480")));
        assertThat(model.tax(), comparesEqualTo(new BigDecimal("192.40")));
        assertThat(model.fullIncome(), comparesEqualTo(new BigDecimal("1287.60")));
        // к 9500 вложенным
        assertThat(model.fullIncomePercent(), comparesEqualTo(new BigDecimal("13.55")));
        // −9510 в день покупки, +500 через 182 дня, +10490 через 364 дня
        assertThat(model.xirr(), comparesEqualTo(new BigDecimal("16.01")));
        // НКД не вводили - посчитан по графику
        assertThat(income.trades().stream().map(TradeModel::aciCalculated).toList(), contains(true, true));
    }

    /**
     * Частичная продажа закрывает самые ранние покупки (FIFO); купон получают облигации, купленные раньше дня выплаты
     * и не проданные раньше него.
     */
    @Test
    void partialSaleClosesEarliestPurchasesFirst() {
        final LocalDate today = DAY_0.plusDays(200);
        final BondModel bond = bond("1000", "100", "0", DAY_0.plusYears(5),
                List.of(couponOn(DAY_0.plusDays(100), "10"), couponOn(DAY_0.plusDays(140), "10"),
                        couponOn(DAY_0.plusDays(160), "10"), couponOn(DAY_0.plusDays(160 + 91), "10")),
                List.of());
        final PortfolioPosition first = lot(1L, DAY_0.plusDays(90), 10, "100", BigDecimal.ZERO, null);
        final PortfolioPosition second = lot(2L, DAY_0.plusDays(120), 5, "100", BigDecimal.ZERO, null);
        // продано 12: все 10 первой покупки и 2 из второй
        final BondIncome income = income(today, bond, List.of(second, first),
                List.of(sale(1L, DAY_0.plusDays(150), 12, "100", BigDecimal.ZERO, null)));
        final IncomeModel model = IncomeAggregator.of("rub", List.of(income), false, null);

        assertThat(income.openQuantity(), equalTo(3));
        // день 100 - 10 облигаций, день 140 - 15, день 160 - 3
        assertThat(model.couponIncome(), comparesEqualTo(new BigDecimal("280")));
        assertThat(model.currentAmount(), comparesEqualTo(new BigDecimal("3000")));
        assertThat(model.tax(), comparesEqualTo(BigDecimal.ZERO));
    }

    @Test
    void maturedBondIsRedeemedAndClosed() {
        final LocalDate maturity = DAY_0.plusDays(100);
        final BondModel bond = bond("1000", "100", "0", maturity,
                List.of(couponOn(DAY_0.plusDays(10), "20"), couponOn(maturity, "20")), List.of());
        final BondIncome income = income(DAY_0.plusDays(120), bond,
                List.of(lot(1L, DAY_0.plusDays(10), 4, "99", BigDecimal.ZERO, null)), List.of());
        final IncomeModel model = IncomeAggregator.of("rub", List.of(income), false, null);

        assertThat(income.openQuantity(), equalTo(0));
        assertThat(income.closeDate(), equalTo(maturity));
        assertThat(income.trades().getLast().type(), equalTo(TradeType.REDEMPTION));
        // купон в день погашения - наш, погашается номинал 4 × 1000
        assertThat(model.couponIncome(), comparesEqualTo(new BigDecimal("80")));
        assertThat(model.soldAmount(), comparesEqualTo(new BigDecimal("4000")));
        assertThat(model.currentAmount(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(model.priceIncome(), comparesEqualTo(new BigDecimal("40")));
    }

    /** Полученный купон без размера в графике: доход неполный, XIRR не считается */
    @Test
    void unknownCouponMakesIncomeIncomplete() {
        final BondModel bond = bond("1000", "100", "0", DAY_0.plusYears(2),
                List.of(couponOn(DAY_0.plusDays(30), "0"), couponOn(DAY_0.plusDays(200), "15")), List.of());
        final BondIncome income = income(DAY_0.plusDays(60), bond,
                List.of(lot(1L, DAY_0, 1, "100", BigDecimal.ZERO, null)), List.of());
        final IncomeModel model = IncomeAggregator.of("rub", List.of(income), false, null);

        assertThat(model.unknownCouponCount(), equalTo(1));
        assertThat(model.incomplete(), equalTo(true));
        assertThat(model.fullIncome(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(model.xirr(), nullValue());
    }

    /** Нет текущей цены у непроданных облигаций - доход посчитать нельзя, полученное видно */
    @Test
    void noCurrentPriceGivesNoIncome() {
        final BondModel bond = bond("1000", "100", "0", DAY_0.plusYears(2),
                List.of(couponOn(DAY_0.plusDays(30), "15")), List.of());
        bond.getPrice().setPercentagePrice(null);
        final BondIncome income = income(DAY_0.plusDays(60), bond,
                List.of(lot(1L, DAY_0, 2, "100", BigDecimal.ZERO, null)), List.of());
        final IncomeModel model = IncomeAggregator.of("rub", List.of(income), false, null);

        assertThat(model.couponIncome(), comparesEqualTo(new BigDecimal("30")));
        assertThat(model.currentAmount(), nullValue());
        assertThat(model.fullIncome(), nullValue());
        assertThat(model.xirr(), nullValue());
        assertThat(model.incomplete(), equalTo(true));
    }

    /** Покупка раньше хранимой истории выплат (3 года) - купоны до неё неизвестны, итог неполный */
    @Test
    void purchaseBeforeStoredHistoryIsIncomplete() {
        final BondModel bond = bond("1000", "100", "0", DAY_0.plusYears(5), List.of(), List.of());
        final BondIncome income = income(DAY_0, bond,
                List.of(lot(1L, DAY_0.minusYears(4), 1, "100", BigDecimal.ZERO, null)), List.of());

        assertThat(IncomeAggregator.of("rub", List.of(income), false, null).incomplete(), equalTo(true));
    }

    /** Налог - с каждой облигации отдельно: убыток по одной не уменьшает налог по другой */
    @Test
    void taxIsPerBondWithoutNetting() {
        final BondModel bond = bond("1000", "110", "0", DAY_0.plusYears(5), List.of(), List.of());
        final BondModel loser = bond("1000", "90", "0", DAY_0.plusYears(5), List.of(), List.of());
        final LocalDate today = DAY_0.plusDays(30);
        final BondIncome profit = income(today, bond, List.of(lot(1L, DAY_0, 1, "100", BigDecimal.ZERO, null)),
                List.of());
        final BondIncome loss = income(today, loser, List.of(lot(2L, DAY_0, 1, "100", BigDecimal.ZERO, null)),
                List.of());
        final IncomeModel total = IncomeAggregator.of("rub", List.of(profit, loss), false, TAX_13);

        assertThat(total.profit(), comparesEqualTo(BigDecimal.ZERO));
        // 13% со 100 по прибыльной облигации
        assertThat(total.tax(), comparesEqualTo(new BigDecimal("13")));
        assertThat(total.fullIncome(), comparesEqualTo(new BigDecimal("-13")));
    }

    /** XIRR позиции моложе 30 дней не считается: годовая ставка за неделю раздувается до тысяч процентов */
    @Test
    void xirrNeedsThirtyDays() {
        final BondModel bond = bond("1000", "101", "0", DAY_0.plusYears(5), List.of(), List.of());
        final IncomeModel week = IncomeAggregator.of("rub", List.of(income(DAY_0.plusDays(7), bond,
                List.of(lot(1L, DAY_0, 1, "100", BigDecimal.ZERO, null)), List.of())), false, null);
        final IncomeModel month = IncomeAggregator.of("rub", List.of(income(DAY_0.plusDays(30), bond,
                List.of(lot(1L, DAY_0, 1, "100", BigDecimal.ZERO, null)), List.of())), false, null);

        assertThat(week.fullIncome(), comparesEqualTo(BigDecimal.TEN));
        assertThat(week.xirr(), nullValue());
        assertThat(month.xirr(), notNullValue());
    }

    /** Части дохода в копейках складываются ровно в полный доход, хотя каждая округлена отдельно */
    @Test
    void breakdownAddsUpToTotal() {
        final BondModel bond = bond("1000", "100.333", "1.111", DAY_0.plusYears(2),
                List.of(couponOn(DAY_0.plusDays(20), "10.005"), couponOn(DAY_0.plusDays(200), "10.005")), List.of());
        final BondIncome income = income(DAY_0.plusDays(60), bond,
                List.of(lot(1L, DAY_0, 3, "99.777", new BigDecimal("0.333"), "0.335")), List.of());
        final IncomeModel model = IncomeAggregator.of("rub", List.of(income), false, TAX_13);

        assertThat(model.priceIncome().add(model.couponIncome()).add(model.aciIncome()).subtract(model.commission())
                .subtract(model.tax()), comparesEqualTo(model.fullIncome()));
    }

    @Test
    void xirrMatchesExcelExample() {
        // пример из документации функции XIRR в Excel: 0,373362535
        assertThat(Xirr.percent(List.of(
                flow(LocalDate.of(2008, 1, 1), "-10000"),
                flow(LocalDate.of(2008, 3, 1), "2750"),
                flow(LocalDate.of(2008, 10, 30), "4250"),
                flow(LocalDate.of(2009, 2, 15), "3250"),
                flow(LocalDate.of(2009, 4, 1), "2750"))), comparesEqualTo(new BigDecimal("37.34")));
        assertThat(Xirr.percent(List.of(flow(DAY_0, "-1000"), flow(DAY_0.plusDays(365), "1100"))),
                comparesEqualTo(new BigDecimal("10.00")));
        // убыток - отрицательная ставка
        assertThat(Xirr.percent(List.of(flow(DAY_0, "-1000"), flow(DAY_0.plusDays(365), "900"))),
                comparesEqualTo(new BigDecimal("-10.00")));
    }

    @Test
    void xirrIsNullWhenItCannotBeCalculated() {
        // всё в один день
        assertThat(Xirr.percent(List.of(flow(DAY_0, "-1000"), flow(DAY_0, "1010"))), nullValue());
        // только вложения
        assertThat(Xirr.percent(List.of(flow(DAY_0, "-1000"), flow(DAY_0.plusDays(10), "-10"))), nullValue());
    }

    private static Xirr.CashFlow flow(final LocalDate date, final String amount) {
        return new Xirr.CashFlow(date, new BigDecimal(amount));
    }

    private static BondIncome income(final LocalDate today, final BondModel bond, final List<PortfolioPosition> lots,
                                     final List<PortfolioSale> sales) {
        final DateTimeMapper dateTimeMapper = clock(today);
        final PriceConversionService conversion = mock(PriceConversionService.class);
        when(conversion.getRubRate(anyString())).thenReturn(BigDecimal.ONE);
        when(conversion.getRubRate(anyString(), any())).thenReturn(BigDecimal.ONE);
        final PortfolioIncomeCalculator calculator = new PortfolioIncomeCalculator(calculator(today),
                mock(CurrencyPriceUseCase.class), dateTimeMapper);
        return calculator.calculate(bond.getTicker(), bond, lots, sales, Map.of(), new RubRates(conversion));
    }

    private static ProfitCalculationServiceImpl calculator(final LocalDate today) {
        return new ProfitCalculationServiceImpl(mock(CurrencyPriceUseCase.class), clock(today));
    }

    private static DateTimeMapper clock(final LocalDate today) {
        final DateTimeMapper dateTimeMapper = mock(DateTimeMapper.class);
        when(dateTimeMapper.getNow()).thenReturn(today.atTime(12, 0));
        return dateTimeMapper;
    }

    private static PortfolioPosition lot(final Long id, final LocalDate date, final int quantity, final String price,
                                         final BigDecimal aci, final String commission) {
        return new PortfolioPosition().setId(id).setTicker("TEST").setBuyDate(date).setQuantity(quantity)
                .setBuyPrice(new BigDecimal(price)).setAci(aci)
                .setCommission(commission == null ? null : new BigDecimal(commission));
    }

    private static PortfolioSale sale(final Long id, final LocalDate date, final int quantity, final String price,
                                      final BigDecimal aci, final String commission) {
        return new PortfolioSale().setId(id).setTicker("TEST").setSellDate(date).setQuantity(quantity)
                .setSellPrice(new BigDecimal(price)).setAci(aci)
                .setCommission(commission == null ? null : new BigDecimal(commission));
    }

    private static BondModel bond(final String nominal, final String percentagePrice, final String aci,
                                  final LocalDate maturity, final List<CouponDataModel> coupons,
                                  final List<AmortizationModel> amortizations) {
        return new BondModel()
                .setTicker("TEST")
                .setName("Тестовая")
                .setCurrency("rub")
                .setMaturityDate(maturity.atStartOfDay())
                .setCoupon(new CouponModel().setQuantityPerYear(4).setIsFixed(true)
                        .setCouponData(new ArrayList<>(coupons)))
                .setAmortizations(new ArrayList<>(amortizations))
                .setPrice(new PriceModel()
                        .setNominal(rub(nominal))
                        .setPercentagePrice(new BigDecimal(percentagePrice))
                        .setAci(rub(aci)));
    }

    private static CouponDataModel coupon(final int daysFromDayZero, final String amount) {
        return couponOn(DAY_0.plusDays(daysFromDayZero), amount);
    }

    private static CouponDataModel couponOn(final LocalDate date, final String amount) {
        return new CouponDataModel().setPaymentDate(date.atStartOfDay()).setPrice(rub(amount));
    }

    private static AmortizationModel amortization(final int daysFromDayZero, final String amount) {
        return new AmortizationModel().setPaymentDate(DAY_0.plusDays(daysFromDayZero).atStartOfDay())
                .setPrice(rub(amount));
    }

    private static MoneyModel rub(final String amount) {
        return new MoneyModel().setQuantity(new BigDecimal(amount)).setCurrency("rub");
    }
}
