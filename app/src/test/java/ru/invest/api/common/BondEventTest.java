package ru.invest.api.common;

import org.junit.jupiter.api.Test;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.event.BondEventModel;
import ru.invest.api.common.model.event.BondEventType;
import ru.invest.api.common.usecase.impl.BondEventUseCaseImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

public class BondEventTest {
    private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

    private final BondEventUseCaseImpl bondEventUseCase = new BondEventUseCaseImpl();

    @Test
    public void floatingCouponChangesAreMarkedTest() {
        final BondModel bond = new BondModel()
                .setCoupon(new CouponModel().setCouponData(List.of(
                        // до начала окна - не попадает в события, но с ним сравнивается первый купон окна
                        coupon(LocalDate.of(2026, 7, 14), "15.00"),
                        coupon(LocalDate.of(2026, 9, 14), "15.21"),
                        coupon(LocalDate.of(2026, 8, 14), "15.00"),
                        // размер ещё не объявлен
                        coupon(LocalDate.of(2026, 10, 14), "0"),
                        coupon(LocalDate.of(2026, 11, 14), "15.21"),
                        coupon(LocalDate.of(2026, 12, 14), "14.80"))))
                .setMaturityDate(LocalDate.of(2027, 1, 14).atStartOfDay())
                .setPrice(new PriceModel().setNominal(money("1000")));

        final List<BondEventModel> events = bondEventUseCase.getEvents(bond, FROM);

        assertThat(events.stream().map(BondEventModel::getDate).toList(), contains(
                LocalDate.of(2026, 8, 14), LocalDate.of(2026, 9, 14), LocalDate.of(2026, 10, 14),
                LocalDate.of(2026, 11, 14), LocalDate.of(2026, 12, 14), LocalDate.of(2027, 1, 14)));
        // 14.08 - как в июле, 14.09 - вырос
        assertThat(events.get(0).getPreviousAmount(), nullValue());
        assertThat(events.get(1).getPreviousAmount().getQuantity(), comparesEqualTo(new BigDecimal("15.00")));
        assertThat(events.get(2).getAmount(), nullValue());
        // необъявленный купон не сбивает сравнение: 14.11 сравнивается с 14.09
        assertThat(events.get(3).getPreviousAmount(), nullValue());
        assertThat(events.get(4).getPreviousAmount().getQuantity(), comparesEqualTo(new BigDecimal("15.21")));
        assertThat(events.getLast().getType(), equalTo(BondEventType.MATURITY));
        assertThat(events.getLast().getAmount().getQuantity(), comparesEqualTo(new BigDecimal("1000")));
    }

    @Test
    public void offerAmortizationAndMaturityTest() {
        final LocalDate maturity = LocalDate.of(2027, 3, 1);
        final BondModel bond = new BondModel()
                .setCoupon(new CouponModel().setCouponData(List.of(coupon(maturity, "20"))))
                .setOfferDate(LocalDate.of(2026, 9, 1).atStartOfDay())
                .setMaturityDate(maturity.atStartOfDay())
                .setPrice(new PriceModel().setNominal(money("500")))
                .setAmortizations(List.of(
                        // уже прошла - вне окна
                        amortization(LocalDate.of(2026, 3, 1), "250"),
                        amortization(LocalDate.of(2026, 12, 1), "250"),
                        amortization(maturity, "250")));

        final List<BondEventModel> events = bondEventUseCase.getEvents(bond, FROM);

        assertThat(events.stream().map(BondEventModel::getType).toList(), contains(
                BondEventType.OFFER, BondEventType.AMORTIZATION, BondEventType.COUPON, BondEventType.AMORTIZATION,
                BondEventType.MATURITY));
        assertThat(events.getFirst().getAmount(), nullValue());
        // последняя часть номинала уже в амортизации - у погашения своей суммы нет, иначе номинал посчитался бы дважды
        assertThat(events.getLast().getAmount(), nullValue());
    }

    @Test
    public void bondWithoutDataHasNoEventsTest() {
        assertThat(bondEventUseCase.getEvents(new BondModel(), FROM), equalTo(List.of()));
    }

    private static CouponDataModel coupon(final LocalDate date, final String amount) {
        return new CouponDataModel().setPaymentDate(date.atStartOfDay()).setPrice(money(amount));
    }

    private static AmortizationModel amortization(final LocalDate date, final String amount) {
        return new AmortizationModel().setPaymentDate(date.atStartOfDay()).setPrice(money(amount));
    }

    private static MoneyModel money(final String amount) {
        return new MoneyModel().setQuantity(new BigDecimal(amount)).setCurrency("rub");
    }
}
