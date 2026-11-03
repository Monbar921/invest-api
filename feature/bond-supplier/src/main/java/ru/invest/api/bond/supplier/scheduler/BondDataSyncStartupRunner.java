package ru.invest.api.bond.supplier.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Прогон ночной цепочки сразу после старта приложения (sync.run-on-startup).
 * <p>
 * Цепочка идёт в отдельном потоке: слушатели ApplicationReadyEvent выполняются в main, и пока они не вернутся,
 * Spring Boot не переводит приложение в ACCEPTING_TRAFFIC - health отвечал бы 503 всё время синхронизации,
 * и Kubernetes перезапускал бы под по startup-пробе, не дав цепочке закончиться.
 * <p>
 * sync() вызывается через бин {@link BondDataSyncScheduler}, а не изнутри него: так срабатывает блокировка ShedLock
 * и прогон не пересекается с ночным запуском и обновлением цен.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.stock.supplier.scheduler.sync", name = {"enabled", "run-on-startup"},
        havingValue = "true")
public class BondDataSyncStartupRunner {
    private static final String THREAD_NAME = "bond-data-sync-startup";

    private final BondDataSyncScheduler bondDataSyncScheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("Bond data sync on startup is scheduled in background");
        Thread.ofVirtual()
                .name(THREAD_NAME)
                .start(bondDataSyncScheduler::sync);
    }
}
