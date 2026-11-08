package ru.invest.api.dto.alert;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.ZonedDateTime;

/**
 * Правило уведомления пользователя по облигации.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class AlertDto {
    private Long id;
    private String ticker;
    /** null - облигации уже нет в системе */
    private String bondName;
    private String isin;
    private AlertTypeDto type;
    /** За сколько дней до события, 1..60 */
    private Integer days;
    private boolean enabled;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime createdAt;
    /** Когда правило последний раз поставило уведомление в очередь */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime lastTriggeredAt;
}
