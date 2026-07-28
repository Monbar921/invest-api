package ru.invest.api.tinkoff.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import ru.invest.api.tinkoff.supplier.usecase.BondSyncUseCase;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.stock.supplier.scheduler.bond", name = "enabled", havingValue = "true")
public class BondSyncScheduler {
    private final BondSyncUseCase bondSyncUseCase;

//    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.bond.cron}")
    public void syncBonds() {
        log.info("Bond sync scheduler started");
        bondSyncUseCase.syncAll();
        log.info("Bond sync scheduler finished");
    }
}
