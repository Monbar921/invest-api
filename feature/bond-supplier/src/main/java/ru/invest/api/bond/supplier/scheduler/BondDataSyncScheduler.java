package ru.invest.api.bond.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.usecase.CouponSyncUseCase;
import ru.invest.api.common.usecase.PriceSyncUseCase;

import java.util.List;

import static ru.invest.api.common.constants.SchedulerConstants.BOND_DATA_SYNC_LOCK;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

/**
 * Ночная цепочка синхронизации: облигации -> купоны -> цены (вместе с пересчётом доходности купонов).
 * Шаги идут строго по очереди; упавший шаг логируется, цепочка продолжается на уже имеющихся данных.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.stock.supplier.scheduler.sync", name = "enabled", havingValue = "true")
public class BondDataSyncScheduler {
    private final BondUseCase bondUseCase;
    private final List<CouponSyncUseCase> couponSyncUseCases;
    private final List<PriceSyncUseCase> priceSyncUseCases;
    private final AuditMapper auditMapper;

    @Value("${ru.invest.api.stock.supplier.scheduler.sync.steps.bond:true}")
    private final boolean bondStepEnabled;
    @Value("${ru.invest.api.stock.supplier.scheduler.sync.steps.coupon:true}")
    private final boolean couponStepEnabled;
    @Value("${ru.invest.api.stock.supplier.scheduler.sync.steps.price:true}")
    private final boolean priceStepEnabled;

    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.sync.cron}", zone = "${ru.invest.api.stock.supplier.scheduler.sync.zone:}")
    @SchedulerLock(name = BOND_DATA_SYNC_LOCK, lockAtLeastFor = "PT5M", lockAtMostFor = "PT2H")
    public void sync() {
        log.info("Bond data sync scheduler started");
        final AuditModel audit = auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS);

        runStep("bond", bondStepEnabled, () -> bondUseCase.syncAll(audit));
        runStep("coupon", couponStepEnabled, () -> couponSyncUseCases
                .stream()
                .findAny()
                .ifPresent(couponSyncUseCase -> couponSyncUseCase.syncAll(audit)));
        runStep("price", priceStepEnabled, () -> priceSyncUseCases
                .stream()
                .findAny()
                .ifPresent(priceSyncUseCase -> priceSyncUseCase.syncAll(audit)));

        log.info("Bond data sync scheduler finished");
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void runStep(final String step, final boolean enabled, final Runnable action) {
        if (!enabled) {
            log.info("Bond data sync step '{}' is disabled, skipped", step);
            return;
        }

        log.info("Bond data sync step '{}' started", step);
        try {
            action.run();
            log.info("Bond data sync step '{}' finished", step);
        } catch (final RuntimeException e) {
            // следующие шаги всё равно полезны на уже имеющихся данных
            log.error("Bond data sync step '{}' failed, continue with next step", step, e);
        }
    }
}
