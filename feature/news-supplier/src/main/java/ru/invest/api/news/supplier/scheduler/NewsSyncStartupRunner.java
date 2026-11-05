package ru.invest.api.news.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Загрузка новостей сразу после старта (news.scheduler.run-on-startup), в фоне - как и у ночной цепочки облигаций,
 * чтобы приложение стало ready, не дожидаясь внешних источников. Вызов через бин - чтобы сработал ShedLock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.news.scheduler", name = {"enabled", "run-on-startup"}, havingValue = "true")
public class NewsSyncStartupRunner {
    private static final String THREAD_NAME = "news-sync-startup";

    private final NewsSyncScheduler newsSyncScheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.info("News sync on startup is scheduled in background");
        Thread.ofVirtual()
                .name(THREAD_NAME)
                .start(newsSyncScheduler::sync);
    }
}
