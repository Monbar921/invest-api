package ru.invest.api.dto.bond.calculation;

/**
 * Как облигация выходит из позиции в конце периода.
 */
public enum ExitTypeDto {
    /** Продажа на бирже по заданной цене */
    SALE,
    /** Выкуп эмитентом по номиналу в дату оферты */
    OFFER,
    /** Погашение по номиналу */
    MATURITY
}
