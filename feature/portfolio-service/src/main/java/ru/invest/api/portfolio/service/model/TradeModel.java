package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Сделка портфеля: покупка, продажа или погашение. Суммы - в валюте номинала, на всё количество.
 *
 * @param id            id покупки или продажи; у погашения null - оно не хранится, а следует из даты погашения
 * @param bondName      null - облигации уже нет в системе
 * @param currency      валюта номинала; null - облигации нет
 * @param price         цена, % от номинала; у погашения - 100
 * @param amount        номинал на дату сделки × цена × количество; null - облигации нет
 * @param aci           НКД сделки: введённый пользователем или по графику купонов; null - посчитать нельзя
 * @param aciCalculated НКД не вводили - он посчитан по графику купонов
 * @param commission    комиссия брокера; null - не вводили
 * @param broker        имя брокера; null - не указан
 */
public record TradeModel(TradeType type,
                         Long id,
                         String ticker,
                         String bondName,
                         String currency,
                         LocalDate date,
                         int quantity,
                         BigDecimal price,
                         BigDecimal amount,
                         BigDecimal aci,
                         boolean aciCalculated,
                         BigDecimal commission,
                         String broker) {
}
