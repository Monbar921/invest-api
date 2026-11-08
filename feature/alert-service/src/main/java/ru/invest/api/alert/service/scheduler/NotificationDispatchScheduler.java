package ru.invest.api.alert.service.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.alert.service.usecase.NotificationDispatchUseCase;

/**
 * Разбор очереди уведомлений по расписанию (по умолчанию раз в 30 секунд): повторы после сбоев связи.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.alerts.dispatcher", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class NotificationDispatchScheduler {
    private static final String DISPATCH_LOCK = "NotificationDispatch";

    private final NotificationDispatchUseCase dispatchUseCase;

    @Scheduled(fixedDelayString = "${ru.invest.api.alerts.dispatcher.interval:PT30S}", initialDelayString = "PT20S")
    @SchedulerLock(name = DISPATCH_LOCK, lockAtMostFor = "PT10M")
    public void dispatch() {
        dispatchUseCase.dispatch();
    }
}
