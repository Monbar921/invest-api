package ru.invest.api.dto.alert;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Правило уведомления пользователя по облигации.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class AlertDto {
    private Long id;
    /** null - правило без облигации (KEY_RATE, SAVED_FILTER) */
    private String ticker;
    /** null - облигации уже нет в системе */
    private String bondName;
    private String isin;
    private AlertTypeDto type;
    /** Сохранённый фильтр - только у SAVED_FILTER */
    private Long filterId;
    /** Название сохранённого фильтра - только у SAVED_FILTER */
    private String filterName;
    /** За сколько дней до события, 1..60; только у правил по датам */
    private Integer days;
    /** Только у правил по цене и доходности */
    private AlertDirectionDto direction;
    /** Уровень (% от номинала или % годовых) или изменение цены в % */
    private BigDecimal threshold;
    /** У PRICE_CHANGE: цена при создании или включении, % от номинала */
    private BigDecimal baseValue;
    /** У уровней: взведено или сработало */
    private AlertStateDto state;
    private boolean enabled;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime createdAt;
    /** Когда правило последний раз поставило уведомление в очередь */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime lastTriggeredAt;
}
