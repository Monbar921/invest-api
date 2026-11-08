package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Покупка облигации: новая позиция или изменение существующей (тикер при изменении не меняется).
 *
 * @param buyPrice цена покупки, % от номинала
 * @param buyDate  null - сегодня
 * @param broker   имя брокера: такой у пользователя уже есть (без учёта регистра) - он, нет - создаётся;
 *                 null или пусто - без брокера
 */
public record PositionRequestModel(String ticker, Integer quantity, BigDecimal buyPrice, LocalDate buyDate,
                                   String broker) {
}
