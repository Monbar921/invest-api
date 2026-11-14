package ru.invest.api.alert.service.model;

import ru.invest.api.common.model.alert.AlertDirection;
import ru.invest.api.common.model.alert.AlertState;
import ru.invest.api.common.model.alert.AlertType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Правило уведомления с подписью облигации.
 *
 * @param bondName        название облигации; null - облигации уже нет в системе
 * @param filterId        у SAVED_FILTER: сохранённый фильтр
 * @param filterName      у SAVED_FILTER: название фильтра
 * @param days            у правил по датам
 * @param direction       у правил по цене и доходности
 * @param threshold       у правил по цене и доходности: уровень или изменение в %
 * @param baseValue       у PRICE_CHANGE: цена при создании или включении, % от номинала
 * @param state           у уровней: взведено или сработало
 * @param createdAt       UTC
 * @param lastTriggeredAt когда правило последний раз поставило уведомление в очередь, UTC
 */
public record AlertModel(Long id,
                         String ticker,
                         String bondName,
                         String isin,
                         AlertType type,
                         Long filterId,
                         String filterName,
                         Integer days,
                         AlertDirection direction,
                         BigDecimal threshold,
                         BigDecimal baseValue,
                         AlertState state,
                         boolean enabled,
                         LocalDateTime createdAt,
                         LocalDateTime lastTriggeredAt) {
}
