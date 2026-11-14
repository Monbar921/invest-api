package ru.invest.api.imports.model;

import java.math.BigDecimal;

/**
 * Сумма в валюте из ответа брокера.
 *
 * @param currency ISO-код в нижнем регистре, как у облигаций в системе (rub, usd)
 */
public record Money(BigDecimal amount, String currency) {
    public boolean isZero() {
        return amount == null || amount.signum() == 0;
    }

    public Money abs() {
        return amount == null ? this : new Money(amount.abs(), currency);
    }
}
