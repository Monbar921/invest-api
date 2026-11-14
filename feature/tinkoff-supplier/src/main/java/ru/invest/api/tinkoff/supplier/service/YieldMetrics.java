package ru.invest.api.tinkoff.supplier.service;

import java.math.BigDecimal;

/**
 * Доходность к погашению и дюрация из того же расчёта: те же выплаты и та же ставка.
 *
 * @param yield            эффективная доходность, % годовых
 * @param durationMacaulay дюрация Маколея, лет: средний срок выплат, взвешенный по их приведённой стоимости
 * @param durationModified модифицированная дюрация, лет: durationMacaulay / (1 + доходность)
 */
public record YieldMetrics(BigDecimal yield, BigDecimal durationMacaulay, BigDecimal durationModified) {
    public static final YieldMetrics EMPTY = new YieldMetrics(null, null, null);
}
