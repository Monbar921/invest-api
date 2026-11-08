package ru.invest.api.alert.service.model;

import ru.invest.api.common.model.alert.AlertType;

import java.time.LocalDateTime;

/**
 * Правило уведомления с подписью облигации.
 *
 * @param bondName        название облигации; null - облигации уже нет в системе
 * @param createdAt       UTC
 * @param lastTriggeredAt когда правило последний раз поставило уведомление в очередь, UTC
 */
public record AlertModel(Long id,
                         String ticker,
                         String bondName,
                         String isin,
                         AlertType type,
                         Integer days,
                         boolean enabled,
                         LocalDateTime createdAt,
                         LocalDateTime lastTriggeredAt) {
}
