package ru.invest.api.portfolio.service.model;

/**
 * Вид сделки портфеля.
 */
public enum TradeType {
    BUY,
    SALE,
    /** Погашение остатка облигаций в дату погашения - не вводится, следует из графика облигации */
    REDEMPTION
}
