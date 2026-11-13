package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Продажа облигаций из портфеля: новая или изменение существующей (тикер при изменении не меняется).
 *
 * @param sellPrice  цена продажи, % от номинала
 * @param sellDate   null - сегодня
 * @param aci        полученный НКД на всю продажу; null - по графику купонов
 * @param commission комиссия брокера; null - 0
 * @param broker     имя брокера - как у покупки
 */
public record SaleRequestModel(String ticker, Integer quantity, BigDecimal sellPrice, LocalDate sellDate,
                               BigDecimal aci, BigDecimal commission, String broker) {
}
