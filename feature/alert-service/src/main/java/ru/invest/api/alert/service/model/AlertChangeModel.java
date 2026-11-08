package ru.invest.api.alert.service.model;

import ru.invest.api.common.model.alert.AlertDirection;

import java.math.BigDecimal;

/**
 * Изменение правила; null - поле не меняется.
 */
public record AlertChangeModel(Integer days, AlertDirection direction, BigDecimal threshold, Boolean enabled) {

    public static AlertChangeModel enabled(final boolean enabled) {
        return new AlertChangeModel(null, null, null, enabled);
    }
}
