package ru.invest.api.common.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.common.usecase.BondSyncUseCase;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.stock.supplier.scheduler.bond", name = "enabled", havingValue = "true")
public class BondSyncScheduler {
    private final List<BondSyncUseCase> bondSyncUseCases;

    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.bond.cron}")
    public void syncBonds() {
        log.info("Bond sync scheduler started");
        bondSyncUseCases
                .stream()
                .findAny()
                .ifPresent(BondSyncUseCase::syncAll);
        log.info("Bond sync scheduler finished");
    }
}
