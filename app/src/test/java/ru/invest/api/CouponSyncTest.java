package ru.invest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.entity.CouponData;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.common.usecase.CouponSyncUseCase;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffCouponApiMapper;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
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
    private InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsServiceBlockingStub;

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void syncAllCouponsTest() {
        final GetBondCouponsResponse fixture = loadCouponFixture();

        when(instrumentsServiceBlockingStub.getBondCoupons(any()))
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

        when(instrumentsServiceBlockingStub.getBondCoupons(any()))
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
