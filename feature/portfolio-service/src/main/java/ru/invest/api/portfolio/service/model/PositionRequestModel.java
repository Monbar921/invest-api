package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Покупка облигации: новая позиция или изменение существующей (тикер при изменении не меняется).
 *
 * @param buyPrice цена покупки, % от номинала
 * @param buyDate  null - сегодня
 */
public record PositionRequestModel(String ticker, Integer quantity, BigDecimal buyPrice, LocalDate buyDate) {
}
