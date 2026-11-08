package ru.invest.api.alert.service.model;

import ru.invest.api.common.model.alert.NotificationStatus;

import java.time.LocalDateTime;

/**
 * Уведомление из истории пользователя. Времена - UTC.
 *
 * @param lastError почему не отправлено (для PENDING - ошибка последней попытки)
 */
public record NotificationModel(Long id,
                                Long alertId,
                                String ticker,
                                String text,
                                NotificationStatus status,
                                int attempts,
                                LocalDateTime createdAt,
                                LocalDateTime sentAt,
                                String lastError) {
}
