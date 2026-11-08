package ru.invest.api.alert.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.alert.service.config.AlertProperties;
import ru.invest.api.alert.service.sender.DeliveryResult;
import ru.invest.api.alert.service.sender.NotificationSender;
import ru.invest.api.alert.service.sender.OutgoingNotification;
import ru.invest.api.alert.service.usecase.NotificationDispatchUseCase;
import ru.invest.api.common.entity.Notification;
import ru.invest.api.common.model.alert.NotificationStatus;
import ru.invest.api.common.repository.NotificationRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchUseCaseImpl implements NotificationDispatchUseCase {
    private static final Duration FIRST_RETRY_DELAY = Duration.ofMinutes(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(30);
    // 2^5 минут уже больше максимума: дальше степень не растёт, чтобы не переполнить сдвиг
    private static final int MAX_BACKOFF_SHIFT = 5;
    private static final String NO_CHANNEL = "Канал уведомлений выключен: Telegram-бот не включён на сервере";

    private final AlertProperties properties;
    private final NotificationRepository notificationRepository;
    private final ObjectProvider<NotificationSender> senders;
    // разбор по расписанию и «сразу после правила» в одном экземпляре не должны идти одновременно
    private final ReentrantLock dispatchLock = new ReentrantLock();

    @Override
    public int dispatch() {
        if (!dispatchLock.tryLock()) {
            return 0;
        }
        try {
            final LocalDateTime now = now();
            final List<Notification> due = notificationRepository.findDue(NotificationStatus.PENDING, now,
                    PageRequest.of(0, properties.getDispatcher().getBatchSize()));
            final NotificationSender sender = senders.getIfAvailable();
            int sent = 0;
            for (final Notification notification : due) {
                final DeliveryResult result = sender == null
                        ? DeliveryResult.failed(NO_CHANNEL)
                        : sender.send(new OutgoingNotification(notification.getId(), notification.getUserId(),
                                notification.getAlertId(), notification.getTicker(), notification.getText()));
                apply(notification, result, now());
                notificationRepository.save(notification);
                if (result.outcome() == DeliveryResult.Outcome.SENT) {
                    sent++;
                }
            }
            if (!due.isEmpty()) {
                log.info("Notifications dispatched: {} due, {} sent", due.size(), sent);
            }
            return sent;
        } finally {
            dispatchLock.unlock();
        }
    }

    @Override
    public void dispatchSoon() {
        if (properties.getDispatcher().isEnabled()) {
            Thread.ofVirtual().name("notification-dispatch").start(this::dispatch);
        }
    }

    @Override
    @Transactional
    public int cleanUp() {
        return notificationRepository.deleteFinishedBefore(NotificationStatus.PENDING,
                now().minus(properties.getRetention()));
    }

    private void apply(final Notification notification, final DeliveryResult result, final LocalDateTime now) {
        notification.setAttempts(notification.getAttempts() + 1);
        notification.setLastError(result.error());
        switch (result.outcome()) {
            case SENT -> notification.setStatus(NotificationStatus.SENT).setSentAt(now);
            case FAILED -> notification.setStatus(NotificationStatus.FAILED);
            case RETRY -> {
                if (notification.getCreatedAt().plus(properties.getGiveUpAfter()).isBefore(now)) {
                    notification.setStatus(NotificationStatus.FAILED)
                            .setLastError("Не отправлено за %d ч: %s".formatted(
                                    properties.getGiveUpAfter().toHours(), result.error()));
                } else {
                    final Duration delay = backoff(notification.getAttempts());
                    final Duration wait = result.retryAfter() != null && result.retryAfter().compareTo(delay) > 0
                            ? result.retryAfter()
                            : delay;
                    notification.setNextAttemptAt(now.plus(wait));
                }
            }
            default -> throw new IllegalStateException("Unknown outcome " + result.outcome());
        }
    }

    /** 1, 2, 4, 8, 16, 30, 30... минут */
    static Duration backoff(final int attempts) {
        final Duration delay = FIRST_RETRY_DELAY.multipliedBy(1L << Math.min(Math.max(attempts - 1, 0), MAX_BACKOFF_SHIFT));
        return delay.compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
