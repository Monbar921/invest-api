package ru.invest.api.bond.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.usecase.PriceSyncUseCase;

import java.util.List;

import static ru.invest.api.common.constants.SchedulerConstants.BOND_DATA_SYNC_LOCK;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

/**
 * Частое обновление цен (и доходности купонов) в течение торгового дня.
 * Делит блокировку с {@link BondDataSyncScheduler}: пока идёт ночная цепочка, прогон пропускается.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.stock.supplier.scheduler.price", name = "enabled", havingValue = "true")
public class PriceSyncScheduler {
    private final List<PriceSyncUseCase> priceSyncUseCases;
    private final AuditMapper auditMapper;

    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.price.cron}", zone = "${ru.invest.api.stock.supplier.scheduler.price.zone:}")
    @SchedulerLock(name = BOND_DATA_SYNC_LOCK, lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void syncPrices() {
        log.info("Price sync scheduler started");
        priceSyncUseCases
                .stream()
                .findAny()
                .ifPresent(priceSyncUseCase -> priceSyncUseCase.syncAll(
                        auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS)
                ));
        log.info("Price sync scheduler finished");
    }
}
