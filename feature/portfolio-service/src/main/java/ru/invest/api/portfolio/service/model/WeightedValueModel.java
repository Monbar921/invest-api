package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;

/**
 * Средневзвешенная по стоимости величина.
 *
 * @param value        среднее по позициям, у которых величина есть; null - ни у одной нет
 * @param coveredShare какая доля стоимости портфеля вошла в среднее, %
 */
public record WeightedValueModel(BigDecimal value, BigDecimal coveredShare) {
}
