package ru.invest.api.common.model.candle;

/**
 * Интервал свечей графика. Хранятся только дневные, неделя и месяц собираются из них.
 */
public enum CandleInterval {
    DAY,
    /** Неделя с понедельника */
    WEEK,
    MONTH
}
