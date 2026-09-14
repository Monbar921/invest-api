package ru.invest.api.bond.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import ru.invest.api.common.usecase.BondSyncUseCase;

@EnableSchedulerLock(defaultLockAtMostFor = "PT60S", defaultLockAtLeastFor = "PT30S")
@EnableScheduling
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = "", havingValue = "true")
public class BondSyncScheduler {
    private final BondSyncUseCase bondSyncUseCase;

    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.bond.enabled}")
    @SchedulerLock(name = "BondSyncScheduler_syncBonds", lockAtLeastFor = "PT5M", lockAtMostFor = "PT15M")
    public void syncBonds() {
        bondSyncUseCase.syncAll();
    }
}
