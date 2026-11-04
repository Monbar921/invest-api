package ru.invest.api.feature.bond.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.invest.api.bond.supplier.scheduler.BondDataSyncScheduler;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.usecase.AmortizationSyncUseCase;
import ru.invest.api.common.usecase.BondSyncUseCase;
import ru.invest.api.common.usecase.CandleSyncUseCase;
import ru.invest.api.common.usecase.CouponSyncUseCase;
import ru.invest.api.common.usecase.PriceSyncUseCase;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

public class BondDataSyncSchedulerTest {
    private final BondSyncUseCase bondSyncUseCase = mock(BondSyncUseCase.class);
    private final CouponSyncUseCase couponSyncUseCase = mock(CouponSyncUseCase.class);
    private final AmortizationSyncUseCase amortizationSyncUseCase = mock(AmortizationSyncUseCase.class);
    private final PriceSyncUseCase priceSyncUseCase = mock(PriceSyncUseCase.class);
    private final CandleSyncUseCase candleSyncUseCase = mock(CandleSyncUseCase.class);
    private final AuditMapper auditMapper = mock(AuditMapper.class);
    private final AuditModel audit = new AuditModel().setCommittedBy(SCHEDULER_PROCESS);

    @BeforeEach
    public void setUp() {
        when(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS)).thenReturn(audit);
    }

    @Test
    public void runsStepsInOrderTest() {
        scheduler(true, true, true).sync();

        final InOrder inOrder = inOrder(bondSyncUseCase, couponSyncUseCase, amortizationSyncUseCase, priceSyncUseCase,
                candleSyncUseCase);
        inOrder.verify(bondSyncUseCase).syncAll(audit);
        inOrder.verify(couponSyncUseCase).syncAll(audit);
        inOrder.verify(amortizationSyncUseCase).syncAll(audit);
        inOrder.verify(priceSyncUseCase).syncAll(audit);
        inOrder.verify(candleSyncUseCase).syncAll(audit);
    }

    @Test
    public void continuesAfterFailedStepTest() {
        doThrow(new IllegalStateException("tinkoff is unavailable")).when(bondSyncUseCase).syncAll(any());
        doThrow(new IllegalStateException("coupons failed")).when(couponSyncUseCase).syncAll(any());

        scheduler(true, true, true).sync();

        verify(couponSyncUseCase).syncAll(audit);
        verify(amortizationSyncUseCase).syncAll(audit);
        verify(priceSyncUseCase).syncAll(audit);
    }

    @Test
    public void skipsDisabledStepTest() {
        scheduler(true, false, true).sync();

        verify(bondSyncUseCase).syncAll(audit);
        verify(couponSyncUseCase, never()).syncAll(any());
        verify(priceSyncUseCase).syncAll(audit);
    }

    private BondDataSyncScheduler scheduler(final boolean bond, final boolean coupon, final boolean price) {
        return new BondDataSyncScheduler(List.of(bondSyncUseCase), List.of(couponSyncUseCase), List.of(amortizationSyncUseCase),
                List.of(priceSyncUseCase), List.of(candleSyncUseCase), auditMapper, bond, coupon, true, price, true);
    }
}
