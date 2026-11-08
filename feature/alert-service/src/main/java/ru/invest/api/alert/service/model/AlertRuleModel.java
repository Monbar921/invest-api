package ru.invest.api.alert.service.model;

import ru.invest.api.common.model.alert.AlertDirection;
import ru.invest.api.common.model.alert.AlertType;

import java.math.BigDecimal;

/**
 * Новое правило. Для правил по датам нужен days, для правил по цене и доходности - direction и threshold.
 */
public record AlertRuleModel(String ticker, AlertType type, Integer days, AlertDirection direction,
                             BigDecimal threshold) {
}
