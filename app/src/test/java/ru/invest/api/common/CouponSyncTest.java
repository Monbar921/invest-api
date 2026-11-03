package ru.invest.api.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.entity.CouponData;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.common.usecase.CouponSyncUseCase;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffCouponApiMapper;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

public class CouponSyncTest extends AbstractInvestApplicationTest {
    private static final String TICKER = "RU000A10EQ34";

    @Autowired
    private CouponSyncUseCase couponSyncUseCase;
    @Autowired
    private AuditMapper auditMapper;
    @Autowired
    private TinkoffCouponApiMapper tinkoffCouponApiMapper;
    @Autowired
    private BondRepository bondRepository;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private InstrumentsGrpcRateLimitedWrapper instrumentsGrpcWrapper;
    @Autowired
    private DateTimeMapper dateTimeMapper;

    @BeforeEach
    public void setUp(){

    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void syncAllCouponsTest() {
        final GetBondCouponsResponse fixture = loadCouponFixture();

        when(instrumentsGrpcWrapper.getBondCoupons(any()))
                .thenReturn(fixture);

        final Bond bond = bondRepository.findByTicker(TICKER).orElseThrow();
        final AuditModel auditModel = auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS);

        final Map<LocalDateTime, CouponDataModel> expectedByPaymentDate = fixture.getEventsList()
                .stream()
                .collect(Collectors.toMap(
                        event -> tinkoffCouponApiMapper.toCouponDataModel(event, TICKER, bond.getUid(), auditModel).getPaymentDate(),
                        event -> tinkoffCouponApiMapper.toCouponDataModel(event, TICKER, bond.getUid(), auditModel),
                        (first, second) -> first
                ));

        couponSyncUseCase.syncAll(auditModel);

        assertCouponDataSynced(bond, expectedByPaymentDate);
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void syncByTickerCouponsTest() {
        final GetBondCouponsResponse fixture = loadCouponFixture();

        when(instrumentsGrpcWrapper.getBondCoupons(any()))
                .thenReturn(fixture);

        final Bond bond = bondRepository.findByTicker(TICKER).orElseThrow();
        final AuditModel auditModel = auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS);

        final Map<LocalDateTime, CouponDataModel> expectedByPaymentDate = fixture.getEventsList()
                .stream()
                .collect(Collectors.toMap(
                        event -> tinkoffCouponApiMapper.toCouponDataModel(event, TICKER, bond.getUid(), auditModel).getPaymentDate(),
                        event -> tinkoffCouponApiMapper.toCouponDataModel(event, TICKER, bond.getUid(), auditModel),
                        (first, second) -> first
                ));

        couponSyncUseCase.syncByTicker(TICKER, auditModel);

        assertCouponDataSynced(bond, expectedByPaymentDate);
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void requestsWholeScheduleTest() {
        when(instrumentsGrpcWrapper.getBondCoupons(any())).thenReturn(loadCouponFixture());

        couponSyncUseCase.syncByTicker(TICKER, auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));

        // без явного периода API отдаёт будущие купоны только на год вперёд
        final long inTenYears = dateTimeMapper.getNow().plusYears(10).toEpochSecond(ZoneOffset.UTC);
        verify(instrumentsGrpcWrapper).getBondCoupons(argThat(request -> request.hasFrom()
                && request.hasTo()
                && request.getTo().getSeconds() > inTenYears));
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons.sql")
    public void apiErrorForOneBondDoesNotStopSyncTest() {
        final List<Bond> bonds = bondRepository.findAll();
        final String failingUid = bonds.getFirst().getUid();
        when(instrumentsGrpcWrapper.getBondCoupons(any())).thenAnswer(invocation -> {
            if (failingUid.equals(invocation.<GetBondCouponsRequest>getArgument(0).getInstrumentId())) {
                throw new IllegalStateException("tinkoff is unavailable");
            }
            return loadCouponFixture();
        });

        couponSyncUseCase.syncAll(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));

        assertCouponsLoadedExcept(bonds, failingUid);
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons.sql")
    public void saveErrorForOneBondDoesNotStopSyncTest() {
        final List<Bond> bonds = bondRepository.findAll();
        final String failingUid = bonds.getFirst().getUid();
        // валюта длиннее varchar2(3) - БД не примет график только этой облигации
        final GetBondCouponsResponse.Builder broken = loadCouponFixture().toBuilder();
        broken.setEvents(0, broken.getEvents(0).toBuilder()
                .setPayOneBond(broken.getEvents(0).getPayOneBond().toBuilder().setCurrency("rubles")));
        when(instrumentsGrpcWrapper.getBondCoupons(any())).thenAnswer(invocation ->
                failingUid.equals(invocation.<GetBondCouponsRequest>getArgument(0).getInstrumentId())
                        ? broken.build()
                        : loadCouponFixture());

        couponSyncUseCase.syncAll(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));

        assertCouponsLoadedExcept(bonds, failingUid);
    }

    private void assertCouponsLoadedExcept(final List<Bond> bonds, final String failingUid) {
        final int expectedSize = loadCouponFixture().getEventsCount();
        for (final Bond bond : bonds) {
            final Coupon coupon = couponRepository.findByUid(bond.getUid()).orElseThrow();
            assertThat(bond.getTicker(), coupon.getCouponData(), hasSize(failingUid.equals(bond.getUid()) ? 0 : expectedSize));
        }
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void savesCouponsWithoutFixDateAndPriceTest() {
        // у далёких купонов дата фиксации реестра, а у флоатеров и размер выплаты ещё не назначены
        final GetBondCouponsResponse.Builder fixture = loadCouponFixture().toBuilder();
        fixture.setEvents(0, fixture.getEvents(0).toBuilder().clearFixDate().clearPayOneBond());
        when(instrumentsGrpcWrapper.getBondCoupons(any())).thenReturn(fixture.build());

        final Bond bond = bondRepository.findByTicker(TICKER).orElseThrow();
        couponSyncUseCase.syncByTicker(TICKER, auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));

        final Coupon actualCoupon = couponRepository.findByUid(bond.getUid()).orElseThrow();
        assertThat(actualCoupon.getCouponData(), hasSize(fixture.getEventsCount()));
        assertThat(actualCoupon.getCouponData()
                .stream()
                .filter(couponData -> couponData.getFixDate() == null && couponData.getPrice() == null)
                .count(), equalTo(1L));
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void reloadsScheduleEndingBeforeMaturityTest() {
        when(instrumentsGrpcWrapper.getBondCoupons(any())).thenReturn(loadCouponFixture());
        final AuditModel audit = auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS);

        // последний купон в фикстуре - 2027-09-21, погашение - 2029-03-14: график оборван, перезагружаем
        couponSyncUseCase.syncAll(audit);
        couponSyncUseCase.syncAll(audit);

        verify(instrumentsGrpcWrapper, times(2)).getBondCoupons(any());
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql",
            statements = "update bond set maturity_date = '2027-09-21 00:00:00' where ticker = 'RU000A10EQ34'")
    public void keepsScheduleReachingMaturityTest() {
        when(instrumentsGrpcWrapper.getBondCoupons(any())).thenReturn(loadCouponFixture());
        final AuditModel audit = auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS);

        // фиксированный купон, график доходит до погашения - второй раз не запрашиваем
        couponSyncUseCase.syncAll(audit);
        couponSyncUseCase.syncAll(audit);

        verify(instrumentsGrpcWrapper, times(1)).getBondCoupons(any());
    }

    private void assertCouponDataSynced(final Bond bond, final Map<LocalDateTime, CouponDataModel> expectedByPaymentDate) {
        final Coupon actualCoupon = couponRepository.findByUid(bond.getUid()).orElseThrow();

        assertThat(actualCoupon.getCouponData(), hasSize(expectedByPaymentDate.size()));

        for (final CouponData actual : actualCoupon.getCouponData()) {
            assertThat(expectedByPaymentDate, hasKey(actual.getPaymentDate()));

            final CouponDataModel expected = expectedByPaymentDate.get(actual.getPaymentDate());

            assertThat(actual.getTicker(), equalTo(expected.getTicker()));
            assertThat(actual.getUid(), equalTo(expected.getUid()));
            assertThat(actual.getFixDate(), equalTo(expected.getFixDate()));
            assertThat(actual.getCurrency(), equalTo(expected.getPrice().getCurrency()));
            // BigDecimal.equals() чувствителен к scale (2 в БД против nano-точности в модели),
            // поэтому сравниваем значение, а не представление
            assertThat(actual.getPrice(), comparesEqualTo(expected.getPrice().getQuantity()));

            assertThat(actual.getCreated(), notNullValue());
            assertThat(actual.getCreated().getCommittedBy(), equalTo(SCHEDULER_PROCESS));
            assertThat(actual.getCreated().getCommittedAt(), notNullValue());
        }
    }
}
