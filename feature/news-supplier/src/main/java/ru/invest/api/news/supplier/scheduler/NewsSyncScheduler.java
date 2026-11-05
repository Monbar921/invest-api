package ru.invest.api.news.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.news.supplier.usecase.NewsSyncUseCase;

import static ru.invest.api.common.constants.SchedulerConstants.NEWS_SYNC_LOCK;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

/**
 * Загрузка новостей по расписанию (по умолчанию раз в 15 минут) и при старте - из {@link NewsSyncStartupRunner}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.news.scheduler", name = "enabled", havingValue = "true")
public class NewsSyncScheduler {
    private final NewsSyncUseCase newsSyncUseCase;
    private final AuditMapper auditMapper;

    @Scheduled(cron = "${ru.invest.api.news.scheduler.cron:0 */15 * * * *}", zone = "${ru.invest.api.news.scheduler.zone:}")
    @SchedulerLock(name = NEWS_SYNC_LOCK, lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void sync() {
        log.info("News sync started");
        newsSyncUseCase.syncAll(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));
        log.info("News sync finished");
    }
}
