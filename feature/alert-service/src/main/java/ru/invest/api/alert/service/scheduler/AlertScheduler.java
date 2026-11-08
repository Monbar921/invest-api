package ru.invest.api.alert.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.alert.service.config.AlertProperties;
import ru.invest.api.alert.service.usecase.AlertCheckUseCase;
import ru.invest.api.alert.service.usecase.NotificationDispatchUseCase;

import java.time.LocalDate;

/**
 * Проверка правил по датам - утром (по умолчанию 09:00 МСК, не ночью) и сразу после старта: если ПК был выключен
 * утром, уведомления придут при запуске. Повторная проверка ничего не дублирует.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.alerts.scheduler", name = "enabled", havingValue = "true")
public class AlertScheduler {
    private static final String ALERT_CHECK_LOCK = "AlertCheck";

    private final AlertProperties properties;
    private final AlertCheckUseCase alertCheckUseCase;
    private final NotificationDispatchUseCase dispatchUseCase;

    @Scheduled(cron = "${ru.invest.api.alerts.scheduler.cron:0 0 9 * * *}",
            zone = "${ru.invest.api.alerts.scheduler.zone:Europe/Moscow}")
    @SchedulerLock(name = ALERT_CHECK_LOCK, lockAtLeastFor = "PT30S", lockAtMostFor = "PT30M")
    public void check() {
        final int queued = alertCheckUseCase.checkAll(LocalDate.now(properties.getZone()));
        final int removed = dispatchUseCase.cleanUp();
        if (removed > 0) {
            log.info("Old notifications removed: {}", removed);
        }
        if (queued > 0) {
            dispatchUseCase.dispatchSoon();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread.ofVirtual().name("alert-check-startup").start(this::checkOnStartup);
    }

    // вызов через себя не проходит через прокси ShedLock - на старте блокировка не нужна: проверка идемпотентна
    private void checkOnStartup() {
        log.info("Alert check on startup");
        check();
    }
}
