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
 * @param buyAmountRub сколько рублей заплачено за покупку валютной облигации (без НКД и комиссии); null - по курсу ЦБ
 *                     на дату покупки. У рублёвой облигации не задаётся
 * @param aci          уплаченный НКД на всю покупку, в валюте номинала; null - по графику купонов
 * @param commission   комиссия брокера, в валюте номинала; null - 0
 */
public record PositionRequestModel(String ticker, Integer quantity, BigDecimal buyPrice, LocalDate buyDate,
                                   String broker, BigDecimal buyAmountRub, BigDecimal aci,
                                   BigDecimal commission) {
}
