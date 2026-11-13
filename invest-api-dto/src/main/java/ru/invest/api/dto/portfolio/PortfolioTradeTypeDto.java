package ru.invest.api.dto.portfolio;

/**
 * Вид сделки портфеля.
 */
public enum PortfolioTradeTypeDto {
    BUY,
    SALE,
    /** Погашение остатка в дату погашения - следует из графика облигации, не вводится */
    REDEMPTION
}
