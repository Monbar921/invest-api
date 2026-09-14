package ru.invest.api.bond.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.usecase.BondSyncUseCase;

import java.util.List;

import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.stock.supplier.scheduler.bond", name = "enabled", havingValue = "true")
public class BondSyncScheduler {
    private final List<BondSyncUseCase> bondSyncUseCases;
    private final AuditMapper auditMapper;

    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.bond.cron}")
    @SchedulerLock(name = "BondSyncScheduler_syncBonds", lockAtLeastFor = "PT5M", lockAtMostFor = "PT15M")
    public void syncBonds() {
        log.info("Bond sync scheduler started");
        bondSyncUseCases
                .stream()
                .findAny()
                .ifPresent(bondSyncUseCase -> bondSyncUseCase.syncAll(
                        auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS)
                ));
        log.info("Bond sync scheduler finished");
    }
}
