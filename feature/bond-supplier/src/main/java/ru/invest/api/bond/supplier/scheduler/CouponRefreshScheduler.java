package ru.invest.api.bond.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.usecase.CouponSyncUseCase;

import java.util.List;

import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.stock.supplier.scheduler.coupon", name = "enabled", havingValue = "true")
public class CouponRefreshScheduler {
    private final List<CouponSyncUseCase> couponSyncUseCases;
    private final AuditMapper auditMapper;

    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.coupon.cron}")
    @SchedulerLock(name = "BondSyncScheduler_syncCoupons", lockAtLeastFor = "PT5M", lockAtMostFor = "PT15M")
    public void refreshCoupons() {
        log.info("Coupon refresh scheduler started");
        couponSyncUseCases
                .stream()
                .findAny()
                .ifPresent(couponSyncUseCase -> couponSyncUseCase.syncAll(
                        auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS)
                ));
        log.info("Coupon refresh scheduler finished");
    }
}
