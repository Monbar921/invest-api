package ru.invest.api.portfolio.service.model;

/**
 * Почему позицию портфеля нельзя оценить в рублях.
 */
public enum UnvaluedReason {
    /** Облигации больше нет в системе */
    UNKNOWN_BOND,
    /** Нет текущей цены или номинала */
    NO_PRICE,
    /** Нет курса валюты номинала к рублю */
    NO_RATE
}
