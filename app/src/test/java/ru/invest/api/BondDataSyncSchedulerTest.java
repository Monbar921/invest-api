package ru.invest.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.invest.api.bond.supplier.scheduler.BondDataSyncScheduler;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.AuditModel;
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
    private final BondUseCase bondUseCase = mock(BondUseCase.class);
    private final CouponSyncUseCase couponSyncUseCase = mock(CouponSyncUseCase.class);
    private final PriceSyncUseCase priceSyncUseCase = mock(PriceSyncUseCase.class);
    private final AuditMapper auditMapper = mock(AuditMapper.class);
    private final AuditModel audit = new AuditModel().setCommittedBy(SCHEDULER_PROCESS);

    @BeforeEach
    public void setUp() {
        when(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS)).thenReturn(audit);
    }

    @Test
    public void runsStepsInOrderTest() {
        scheduler(true, true, true).sync();

        final InOrder inOrder = inOrder(bondUseCase, couponSyncUseCase, priceSyncUseCase);
        inOrder.verify(bondUseCase).syncAll(audit);
        inOrder.verify(couponSyncUseCase).syncAll(audit);
        inOrder.verify(priceSyncUseCase).syncAll(audit);
    }

    @Test
    public void continuesAfterFailedStepTest() {
        doThrow(new IllegalStateException("tinkoff is unavailable")).when(bondUseCase).syncAll(any());
        doThrow(new IllegalStateException("coupons failed")).when(couponSyncUseCase).syncAll(any());

        scheduler(true, true, true).sync();

        verify(couponSyncUseCase).syncAll(audit);
        verify(priceSyncUseCase).syncAll(audit);
    }

    @Test
    public void skipsDisabledStepTest() {
        scheduler(true, false, true).sync();

        verify(bondUseCase).syncAll(audit);
        verify(couponSyncUseCase, never()).syncAll(any());
        verify(priceSyncUseCase).syncAll(audit);
    }

    private BondDataSyncScheduler scheduler(final boolean bond, final boolean coupon, final boolean price) {
        return new BondDataSyncScheduler(bondUseCase, List.of(couponSyncUseCase), List.of(priceSyncUseCase), auditMapper,
                bond, coupon, price);
    }
}
