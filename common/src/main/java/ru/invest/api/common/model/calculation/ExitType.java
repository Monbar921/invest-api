package ru.invest.api.common.model.calculation;

/**
 * Как облигация выходит из позиции в конце периода.
 */
public enum ExitType {
    /** Продажа на бирже по цене, которую задал пользователь */
    SALE,
    /** Выкуп эмитентом по номиналу в дату оферты */
    OFFER,
    /** Погашение по номиналу */
    MATURITY
}
