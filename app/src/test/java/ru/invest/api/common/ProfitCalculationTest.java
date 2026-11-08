package ru.invest.api.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
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
import ru.invest.api.tinkoff.supplier.service.impl.ProfitCalculationServiceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProfitCalculationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 12, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    private ProfitCalculationServiceImpl profitCalculationService;

    @BeforeEach
    public void setUp() {
        final DateTimeMapper dateTimeMapper = mock(DateTimeMapper.class);
        when(dateTimeMapper.getNow()).thenReturn(NOW);

        profitCalculationService = new ProfitCalculationServiceImpl(mock(CurrencyPriceUseCase.class), dateTimeMapper);
    }

    @Test
    public void lastCouponBeforeMaturityTest() {
        // пример из ручного расчёта: цена 100,66% от номинала 83,70 = 84,2524, последний купон 3,50 и погашение через 49 дней,
        // НКД не доплачиваем: (83,70 - 84,2524 + 3,50) / 84,2524 = 3,50%
        final BondModel bond = bond("83.70", "100.66", "0", 49, coupon(-42, "3.50"), coupon(49, "3.50"));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request());

        assertThat(result.getExitType(), equalTo(ExitType.MATURITY));
        assertThat(result.getSellDate(), equalTo(TODAY.plusDays(49)));
        assertThat(result.getPurchaseAmount(), comparesEqualTo(new BigDecimal("84.25")));
        assertThat(result.getPurchaseAci(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(result.getCouponIncome(), comparesEqualTo(new BigDecimal("3.50")));
        assertThat(result.getExitAmount(), comparesEqualTo(new BigDecimal("83.70")));
        assertThat(result.getProfit(), comparesEqualTo(new BigDecimal("2.95")));
        assertThat(result.getProfitPercent(), comparesEqualTo(new BigDecimal("3.50")));
        // 3,50% за 49 дней ~ 26,1% годовых без сложного процента
        assertThat(result.getAnnualProfitPercent(), comparesEqualTo(new BigDecimal("26.06")));
    }

    @Test
    public void accruedInterestAndTaxTest() {
        // доплатили НКД 1,69: он вернётся в купоне, поэтому доход на облигацию меньше - 87,20 - 85,94 = 1,26
        final BondModel bond = bond("83.70", "100.66", "1.69", 49, coupon(-42, "3.50"), coupon(49, "3.50"));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request()
                .setQuantity(10)
                .setTaxRatePercent(new BigDecimal("13")));

        assertThat(result.getPurchaseAci(), comparesEqualTo(new BigDecimal("16.90")));
        assertThat(result.getProfit(), comparesEqualTo(new BigDecimal("12.58")));
        assertThat(result.getProfitPercent(), comparesEqualTo(new BigDecimal("1.46")));
        // НДФЛ 13% с дохода 12,58
        assertThat(result.getTax(), comparesEqualTo(new BigDecimal("1.63")));
        assertThat(result.getNetProfit(), comparesEqualTo(new BigDecimal("10.94")));
        assertThat(result.getNetProfitPercent(), comparesEqualTo(new BigDecimal("1.27")));
    }

    @Test
    public void taxRate15Test() {
        final BondModel bond = bond("83.70", "100.66", "1.69", 49, coupon(-42, "3.50"), coupon(49, "3.50"));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request()
                .setQuantity(10)
                .setTaxRatePercent(new BigDecimal("15")));

        // 15% с дохода 12,58
        assertThat(result.getTaxRatePercent(), comparesEqualTo(new BigDecimal("15")));
        assertThat(result.getTax(), comparesEqualTo(new BigDecimal("1.89")));
        assertThat(result.getNetProfit(), comparesEqualTo(new BigDecimal("10.69")));
    }

    @Test
    public void withoutTaxTest() {
        final BondModel bond = bond("83.70", "100.66", "1.69", 49, coupon(-42, "3.50"), coupon(49, "3.50"));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request().setQuantity(10));

        // ставка не задана - налог не считаем, доход после налога равен доходу
        assertThat(result.getTaxRatePercent(), nullValue());
        assertThat(result.getTax(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(result.getNetProfit(), comparesEqualTo(result.getProfit()));
        assertThat(result.getNetProfitPercent(), comparesEqualTo(result.getProfitPercent()));
    }

    @Test
    public void depositTest() {
        // 10 облигаций по 100% от 1000 на год: на вклад под 14% те же 10 000 дают 1 400, после НДФЛ 13% - 1 218
        final BondModel bond = bond("1000", "100", "0", 365, coupon(180, "60"), coupon(365, "60"));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request()
                .setQuantity(10)
                .setTaxRatePercent(new BigDecimal("13"))
                .setDepositRatePercent(new BigDecimal("14")));

        assertThat(result.getProfit(), comparesEqualTo(new BigDecimal("1200.00")));
        assertThat(result.getDepositRatePercent(), comparesEqualTo(new BigDecimal("14")));
        assertThat(result.getDepositProfit(), comparesEqualTo(new BigDecimal("1400.00")));
        assertThat(result.getDepositTax(), comparesEqualTo(new BigDecimal("182.00")));
        assertThat(result.getDepositNetProfit(), comparesEqualTo(new BigDecimal("1218.00")));
    }

    @Test
    public void depositWithoutTaxAndWithoutRateTest() {
        final BondModel bond = bond("1000", "100", "0", 73, coupon(73, "20"));

        // без НДФЛ: 1000 x 10% x 73 / 365 = 20
        final ProfitCalculationModel deposit = profitCalculationService.calculate(bond, request()
                .setDepositRatePercent(BigDecimal.TEN));
        assertThat(deposit.getDepositProfit(), comparesEqualTo(new BigDecimal("20.00")));
        assertThat(deposit.getDepositTax(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(deposit.getDepositNetProfit(), comparesEqualTo(new BigDecimal("20.00")));

        // ставка вклада не задана - сравнения нет
        final ProfitCalculationModel withoutDeposit = profitCalculationService.calculate(bond, request());
        assertThat(withoutDeposit.getDepositProfit(), nullValue());
    }

    @Test
    public void lossIsNotTaxedTest() {
        // продажа дешевле покупки: дохода нет - и налога нет
        final BondModel bond = bond("1000", "100", "0", 400, coupon(200, "10"));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request()
                .setSellDate(TODAY.plusDays(100))
                .setSellPricePercent(new BigDecimal("95"))
                .setTaxRatePercent(new BigDecimal("13")));

        assertThat(result.getProfit().signum(), equalTo(-1));
        assertThat(result.getTax(), comparesEqualTo(BigDecimal.ZERO));
    }

    @Test
    public void saleBeforeMaturityTest() {
        // купоны 30 раз в 90 дней; продаём через 75 дней по 101%, НКД при продаже = 30 x (75 - 30) / (120 - 30) = 15
        final BondModel bond = bond("1000", "99", "10", 400, coupon(-60, "30"), coupon(30, "30"), coupon(120, "30"));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request()
                .setSellDate(TODAY.plusDays(75))
                .setSellPricePercent(new BigDecimal("101")));

        assertThat(result.getExitType(), equalTo(ExitType.SALE));
        assertThat(result.getCouponCount(), equalTo(1));
        assertThat(result.getExitAmount(), comparesEqualTo(new BigDecimal("1010.00")));
        assertThat(result.getExitAci(), comparesEqualTo(new BigDecimal("15.00")));
        // 30 + 1010 + 15 - (990 + 10) = 55
        assertThat(result.getProfit(), comparesEqualTo(new BigDecimal("55.00")));
    }

    @Test
    public void saleAtFutureBuyDateAccruesInterestTest() {
        // покупка через 60 дней: НКД считается по графику - 30 x (60 - 30) / (120 - 30) = 10
        final BondModel bond = bond("1000", "99", "10", 400, coupon(-60, "30"), coupon(30, "30"), coupon(120, "30"));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request()
                .setBuyDate(TODAY.plusDays(60))
                .setSellDate(TODAY.plusDays(120)));

        assertThat(result.getPurchaseAci(), comparesEqualTo(new BigDecimal("10.00")));
        // купон в день продажи получаем, НКД при продаже уже нулевой
        assertThat(result.getCouponIncome(), comparesEqualTo(new BigDecimal("30.00")));
        assertThat(result.getExitAci(), comparesEqualTo(BigDecimal.ZERO));
        // цена продажи не задана - текущая 99%: 30 + 990 - (990 + 10) = 20
        assertThat(result.getProfit(), comparesEqualTo(new BigDecimal("20.00")));
    }

    @Test
    public void offerTest() {
        // продажа в дату оферты - выкуп по номиналу, цена продажи из запроса не используется
        final BondModel bond = bond("1000", "97", "0", 400, coupon(100, "40"))
                .setOfferDate(NOW.plusDays(100));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request()
                .setSellDate(TODAY.plusDays(100))
                .setSellPricePercent(new BigDecimal("50")));

        assertThat(result.getExitType(), equalTo(ExitType.OFFER));
        assertThat(result.getSellPricePercent(), comparesEqualTo(new BigDecimal("100")));
        // 40 + 1000 - 970 = 70
        assertThat(result.getProfit(), comparesEqualTo(new BigDecimal("70.00")));
    }

    @Test
    public void amortizationToMaturityTest() {
        // половина номинала гасится через 100 дней, остаток - при погашении
        final BondModel bond = bond("1000", "100", "0", 200, coupon(100, "40"), coupon(200, "20"))
                .setAmortizationFlag(true)
                .setAmortizations(List.of(amortization(100, "500"), amortization(200, "500")));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request());

        assertThat(result.getAmortizationIncome(), comparesEqualTo(new BigDecimal("1000.00")));
        assertThat(result.getExitAmount(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(result.getProfit(), comparesEqualTo(new BigDecimal("60.00")));
    }

    @Test
    public void amortizationAndSaleTest() {
        // после частичного погашения продаётся только остаток номинала 500: 500 x 101% = 505, НКД 20 x 50 / 100 = 10
        final BondModel bond = bond("1000", "100", "0", 200, coupon(100, "40"), coupon(200, "20"))
                .setAmortizationFlag(true)
                .setAmortizations(List.of(amortization(100, "500"), amortization(200, "500")));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request()
                .setSellDate(TODAY.plusDays(150))
                .setSellPricePercent(new BigDecimal("101")));

        assertThat(result.getAmortizationIncome(), comparesEqualTo(new BigDecimal("500.00")));
        assertThat(result.getExitAmount(), comparesEqualTo(new BigDecimal("505.00")));
        assertThat(result.getExitAci(), comparesEqualTo(new BigDecimal("10.00")));
        // 40 + 500 + 505 + 10 - 1000 = 55
        assertThat(result.getProfit(), comparesEqualTo(new BigDecimal("55.00")));
    }

    @Test
    public void unknownCouponsTest() {
        // флоатер: размер второго купона не объявлен - в доход не входит, но расчёт сообщает о нём
        final BondModel bond = bond("1000", "100", "0", 200, coupon(100, "40"), coupon(200, "0"));

        final ProfitCalculationModel result = profitCalculationService.calculate(bond, request());

        assertThat(result.getCouponIncome(), comparesEqualTo(new BigDecimal("40.00")));
        assertThat(result.getUnknownCouponCount(), equalTo(1));
    }

    @Test
    public void amortizationScheduleMissingTest() {
        final BondModel bond = bond("1000", "100", "0", 200, coupon(200, "40")).setAmortizationFlag(true);

        assertThat(profitCalculationService.calculate(bond, request()).isAmortizationScheduleMissing(), equalTo(true));
    }

    @Test
    public void invalidPeriodTest() {
        final BondModel bond = bond("1000", "100", "0", 200, coupon(200, "40"));

        assertThrows(GeneralUnprocessableEntityException.class,
                () -> profitCalculationService.calculate(bond, request().setBuyDate(TODAY.minusDays(1))));
        assertThrows(GeneralUnprocessableEntityException.class,
                () -> profitCalculationService.calculate(bond, request().setSellDate(TODAY)));
        assertThrows(GeneralUnprocessableEntityException.class,
                () -> profitCalculationService.calculate(bond, request().setBuyDate(TODAY.plusDays(200))));
        assertThrows(GeneralUnprocessableEntityException.class,
                () -> profitCalculationService.calculate(bond, request().setQuantity(0)));
        assertThrows(GeneralUnprocessableEntityException.class,
                () -> profitCalculationService.calculate(bond, request().setTaxRatePercent(new BigDecimal("100"))));
        assertThrows(GeneralUnprocessableEntityException.class,
                () -> profitCalculationService.calculate(bond, request().setDepositRatePercent(new BigDecimal("-1"))));
    }

    private static ProfitCalculationRequestModel request() {
        return new ProfitCalculationRequestModel();
    }

    private static BondModel bond(final String nominal, final String percentagePrice, final String aci, final int daysToMaturity,
                                  final CouponDataModel... coupons) {
        return new BondModel()
                .setTicker("TEST")
                .setMaturityDate(NOW.plusDays(daysToMaturity))
                .setCoupon(new CouponModel().setQuantityPerYear(4).setIsFixed(true).setCouponData(List.of(coupons)))
                .setPrice(new PriceModel()
                        .setNominal(rub(nominal))
                        .setPercentagePrice(new BigDecimal(percentagePrice))
                        .setAci(rub(aci)));
    }

    private static CouponDataModel coupon(final int daysFromNow, final String amount) {
        return new CouponDataModel()
                .setPaymentDate(NOW.plusDays(daysFromNow))
                .setPrice(rub(amount));
    }

    private static AmortizationModel amortization(final int daysFromNow, final String amount) {
        return new AmortizationModel()
                .setPaymentDate(NOW.plusDays(daysFromNow))
                .setPrice(rub(amount));
    }

    private static MoneyModel rub(final String amount) {
        return new MoneyModel()
                .setQuantity(new BigDecimal(amount))
                .setCurrency("rub");
    }
}
