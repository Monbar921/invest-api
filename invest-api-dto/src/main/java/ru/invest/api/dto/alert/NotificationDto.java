package ru.invest.api.dto.alert;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.ZonedDateTime;

/**
 * Уведомление из истории пользователя.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NotificationDto {
    private Long id;
    /** null - правило удалено */
    private Long alertId;
    private String ticker;
    private String text;
    private NotificationStatusDto status;
    private int attempts;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime createdAt;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime sentAt;
    /** Почему не отправлено; у PENDING - ошибка последней попытки */
    private String lastError;
}
