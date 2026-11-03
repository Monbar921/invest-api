package ru.invest.api.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.invest.api.common.mapper.DateTimeMapper;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CouponInterestCalculationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);
    private static final BigDecimal PRECISION = new BigDecimal("0.0001");

    private CouponCalculationServiceImpl couponCalculationService;

    @BeforeEach
    public void setUp() {
        final DateTimeMapper dateTimeMapper = mock(DateTimeMapper.class);
        when(dateTimeMapper.getNow()).thenReturn(NOW);

        couponCalculationService = new CouponCalculationServiceImpl(mock(CurrencyPriceUseCase.class), dateTimeMapper);
    }

    @Test
    public void fullYearOfCouponsTest() {
        // 4 купона по 25 при цене 1000 => 10%
        final BigDecimal interest = couponCalculationService.calculateInterest(
                coupon(4, payment(30, "25"), payment(120, "25"), payment(210, "25"), payment(300, "25"), payment(390, "25")),
                bond("1000"));

        assertThat(interest, closeTo(new BigDecimal("10.0000"), PRECISION));
    }

    @Test
    public void lastCouponBeforeMaturityTest() {
        // до погашения 49 дней и один купон из четырёх в год: доходность годовая, а не четверть от неё
        // 3.50 x 4 / 84.25 = 16.6172%
        final BigDecimal interest = couponCalculationService.calculateInterest(
                coupon(4, payment(-42, "3.50"), payment(49, "3.50")), bond("84.25"));

        assertThat(interest, closeTo(new BigDecimal("16.6172"), PRECISION));
    }

    private static CouponModel coupon(final int quantityPerYear, final CouponDataModel... payments) {
        return new CouponModel()
                .setQuantityPerYear(quantityPerYear)
                .setIsFixed(true)
                .setCouponData(List.of(payments));
    }

    private static CouponDataModel payment(final int daysFromNow, final String amount) {
        return new CouponDataModel()
                .setPaymentDate(NOW.plusDays(daysFromNow))
                .setPrice(rub(amount));
    }

    private static BondModel bond(final String price) {
        return new BondModel()
                .setPrice(new PriceModel()
                        .setNominal(rub("1000"))
                        .setCurrent(rub(price)));
    }

    private static MoneyModel rub(final String amount) {
        return new MoneyModel()
                .setQuantity(new BigDecimal(amount))
                .setCurrency("rub");
    }
}
