package ru.invest.api.bond.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import ru.invest.api.bond.supplier.usecase.BondSyncUseCase;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.stock.supplier.scheduler.bond", name = "enabled", havingValue = "true")
public class BondSyncSchedulerCommon {
    private final List<BondSyncUseCase> bondSyncUseCases;

    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.bond.cron}")
    @SchedulerLock(name = "BondSyncScheduler_syncBonds", lockAtLeastFor = "PT5M", lockAtMostFor = "PT15M")
    public void syncBonds() {
        log.info("Bond sync scheduler started");
        bondSyncUseCases
                .stream()
                .findAny()
                .ifPresent(BondSyncUseCase::syncAll);
        log.info("Bond sync scheduler finished");
    }
}
