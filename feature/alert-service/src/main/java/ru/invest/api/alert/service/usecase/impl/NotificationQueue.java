package ru.invest.api.alert.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.common.entity.Notification;
import ru.invest.api.common.model.alert.NotificationStatus;
import ru.invest.api.common.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Постановка в очередь. Повтор с тем же dedupKey (перезапуск, повторная проверка) молча пропускается. Проверка и
 * вставка не атомарны, но проверки идут под ShedLock, а на гонку есть уникальный индекс dedup_key.
 */
@Component
@RequiredArgsConstructor
class NotificationQueue {
    private final NotificationRepository notificationRepository;

    /**
     * @return true - поставлено, false - уже было
     */
    boolean enqueue(final String userId, final Long alertId, final String ticker, final String dedupKey,
                    final String text) {
        if (notificationRepository.existsByDedupKey(dedupKey)) {
            return false;
        }
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        notificationRepository.save(new Notification()
                .setUserId(userId)
                .setAlertId(alertId)
                .setTicker(ticker)
                .setDedupKey(dedupKey)
                .setText(text)
                .setStatus(NotificationStatus.PENDING)
                .setAttempts(0)
                .setNextAttemptAt(now)
                .setCreatedAt(now));
        return true;
    }
}
