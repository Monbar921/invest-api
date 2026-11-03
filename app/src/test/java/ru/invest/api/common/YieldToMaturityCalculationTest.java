package ru.invest.api.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.currency.service.usecase.CurrencyPriceUseCase;
import ru.invest.api.tinkoff.supplier.service.impl.CouponCalculationServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Эталонные значения посчитаны отдельно от сервиса - решением уравнения цена = сумма дисконтированных выплат.
 */
public class YieldToMaturityCalculationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 3, 12, 0);
    private static final String RUB = "rub";
    private static final BigDecimal PRECISION = new BigDecimal("0.0001");

    private CouponCalculationServiceImpl couponCalculationService;

    @BeforeEach
    public void setUp() {
        final DateTimeMapper dateTimeMapper = mock(DateTimeMapper.class);
        when(dateTimeMapper.getNow()).thenReturn(NOW);

        couponCalculationService = new CouponCalculationServiceImpl(mock(CurrencyPriceUseCase.class), dateTimeMapper);
    }

    @Test
    public void yieldToMaturityTest() {
        // купон 80 раз в год, два года до погашения, цена 950 при номинале 1000:
        // 950 = 80 / (1 + r) + 1080 / (1 + r)^2 => r = 10.9164%
        final BigDecimal yield = couponCalculationService.calculateYieldToMaturity(
                coupon(payment(365, "80"), payment(730, "80")), bond("950", 730));

        assertThat(yield, closeTo(new BigDecimal("10.9164"), PRECISION));
    }

    @Test
    public void yieldToMaturityAboveParTest() {
        // цена выше номинала: купон 80 при цене 1000 даёт 8%, переплата 50 сверху снижает доходность
        final BigDecimal yield = couponCalculationService.calculateYieldToMaturity(
                coupon(payment(365, "80")), bond("1050", 365));

        // 1050 = 1080 / (1 + r) => r = 2.8571%
        assertThat(yield, closeTo(new BigDecimal("2.8571"), PRECISION));
    }

    @Test
    public void accruedInterestIncreasesPriceTest() {
        // тот же выпуск, что в yieldToMaturityTest, но к цене 950 доплачиваем НКД 20:
        // 970 = 80 / (1 + r) + 1080 / (1 + r)^2 => r = 9.7221%
        final BondModel bond = bond("950", 730);
        bond.getPrice().setAci(rub("20"));

        final BigDecimal yield = couponCalculationService.calculateYieldToMaturity(
                coupon(payment(365, "80"), payment(730, "80")), bond);

        assertThat(yield, closeTo(new BigDecimal("9.7221"), PRECISION));
    }

    @Test
    public void withoutAccruedInterestTest() {
        // НКД ещё не синхронизирован - доходность была бы завышена
        final BondModel bond = bond("950", 730);
        bond.getPrice().setAci(null);

        assertThat(couponCalculationService.calculateYieldToMaturity(coupon(payment(365, "80")), bond), nullValue());
    }

    @Test
    public void amortizationTest() {
        // половина номинала гасится через год, вторая - при погашении; купон 8% начисляется на остаток номинала,
        // поэтому при покупке по номиналу доходность ровно 8%
        final BondModel bond = bond("1000", 730)
                .setAmortizationFlag(true)
                .setAmortizations(List.of(amortization(365, "500"), amortization(730, "500")));

        final BigDecimal yield = couponCalculationService.calculateYieldToMaturity(
                coupon(payment(365, "80"), payment(730, "40")), bond);

        assertThat(yield, closeTo(new BigDecimal("8.0000"), PRECISION));
    }

    @Test
    public void amortizationWithoutScheduleTest() {
        // график амортизации ещё не загружен - весь номинал ушёл бы на дату погашения
        final BondModel bond = bond("1000", 730).setAmortizationFlag(true);

        assertThat(couponCalculationService.calculateYieldToMaturity(
                coupon(payment(365, "80"), payment(730, "40")), bond), nullValue());
    }

    @Test
    public void amortizationAboveNominalTest() {
        // по графику гасится больше номинала - данные противоречивы
        final BondModel bond = bond("1000", 730)
                .setAmortizationFlag(true)
                .setAmortizations(List.of(amortization(365, "600"), amortization(730, "500")));

        assertThat(couponCalculationService.calculateYieldToMaturity(
                coupon(payment(365, "80"), payment(730, "40")), bond), nullValue());
    }

    @Test
    public void yieldToOfferTest() {
        // оферта через год: купон 100 и выкуп по номиналу 1000 при цене 980 => 1100 / 980 - 1 = 12.2449%
        final BondModel bond = bond("980", 730).setOfferDate(NOW.plusDays(365));
        final CouponModel coupon = coupon(payment(365, "100"), payment(730, "100"));

        assertThat(couponCalculationService.calculateYieldToOffer(coupon, bond), closeTo(new BigDecimal("12.2449"), PRECISION));
        // к погашению учитываются оба купона: 980 = 100 / (1 + r) + 1100 / (1 + r)^2 => r = 11.1705%
        assertThat(couponCalculationService.calculateYieldToMaturity(coupon, bond), closeTo(new BigDecimal("11.1705"), PRECISION));
    }

    @Test
    public void yieldToOfferWithAmortizationTest() {
        // до оферты гасится половина номинала, в оферту выкупается остаток 500 - последнее погашение после оферты не учитывается
        final BondModel bond = bond("1000", 1095)
                .setOfferDate(NOW.plusDays(730))
                .setAmortizationFlag(true)
                .setAmortizations(List.of(amortization(365, "500"), amortization(1095, "500")));

        final BigDecimal yield = couponCalculationService.calculateYieldToOffer(
                coupon(payment(365, "80"), payment(730, "40"), payment(1095, "40")), bond);

        assertThat(yield, closeTo(new BigDecimal("8.0000"), PRECISION));
    }

    @Test
    public void withoutOfferTest() {
        assertThat(couponCalculationService.calculateYieldToOffer(coupon(payment(365, "80")), bond("950", 365)), nullValue());
    }

    @Test
    public void offerAfterMaturityTest() {
        final BondModel bond = bond("950", 365).setOfferDate(NOW.plusDays(365));

        assertThat(couponCalculationService.calculateYieldToOffer(coupon(payment(365, "80")), bond), nullValue());
    }

    @Test
    public void floaterYieldToOfferTest() {
        // размер купона после оферты не объявлен: к погашению посчитать нельзя, к оферте - можно
        final BondModel bond = bond("1000", 730).setOfferDate(NOW.plusDays(365));
        final CouponModel coupon = coupon(payment(365, "100"), payment(730, "0"));

        assertThat(couponCalculationService.calculateYieldToMaturity(coupon, bond), nullValue());
        assertThat(couponCalculationService.calculateYieldToOffer(coupon, bond), closeTo(new BigDecimal("10.0000"), PRECISION));
    }

    @Test
    public void paidCouponsAreIgnoredTest() {
        final BigDecimal yield = couponCalculationService.calculateYieldToMaturity(
                coupon(payment(-30, "80"), payment(365, "80")), bond("1050", 365));

        assertThat(yield, closeTo(new BigDecimal("2.8571"), PRECISION));
    }

    @Test
    public void unknownFutureCouponTest() {
        // флоатер: размер следующего купона ещё не объявлен
        final BigDecimal yield = couponCalculationService.calculateYieldToMaturity(
                coupon(payment(365, "80"), payment(730, "0")), bond("950", 730));

        assertThat(yield, nullValue());
    }

    @Test
    public void maturedBondTest() {
        final BigDecimal yield = couponCalculationService.calculateYieldToMaturity(
                coupon(payment(-1, "80")), bond("950", -1));

        assertThat(yield, nullValue());
    }

    @Test
    public void withoutPriceTest() {
        final BondModel bond = bond("950", 365);
        bond.getPrice().setCurrent(null);

        assertThat(couponCalculationService.calculateYieldToMaturity(coupon(payment(365, "80")), bond), nullValue());
    }

    private static CouponModel coupon(final CouponDataModel... payments) {
        return new CouponModel()
                .setQuantityPerYear(1)
                .setIsFixed(true)
                .setCouponData(List.of(payments));
    }

    private static CouponDataModel payment(final int daysFromNow, final String amount) {
        return new CouponDataModel()
                .setPaymentDate(NOW.plusDays(daysFromNow))
                .setPrice(rub(amount));
    }

    private static AmortizationModel amortization(final int daysFromNow, final String amount) {
        return new AmortizationModel()
                .setPaymentDate(NOW.plusDays(daysFromNow))
                .setPrice(rub(amount));
    }

    private static BondModel bond(final String price, final int daysToMaturity) {
        return new BondModel()
                .setMaturityDate(NOW.plusDays(daysToMaturity))
                .setPrice(new PriceModel()
                        .setNominal(rub("1000"))
                        .setCurrent(rub(price))
                        // сразу после выплаты купона НКД нулевой
                        .setAci(rub("0")));
    }

    private static MoneyModel rub(final String amount) {
        return new MoneyModel()
                .setQuantity(new BigDecimal(amount))
                .setCurrency(RUB);
    }
}
