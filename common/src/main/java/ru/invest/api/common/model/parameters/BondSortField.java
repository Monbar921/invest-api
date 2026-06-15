package ru.invest.api.common.model.parameters;

/**
 * Поля, по которым можно сортировать список облигаций.
 */
public enum BondSortField {
    /** Уровень риска (LOW/MODERATE < HIGH < прочие) */
    RISK_LEVEL,
    /** Годовая доходность купона в % */
    COUPON_INTEREST,
    /** Дата погашения */
    MATURITY_DATE,
    /** Тикер */
    TICKER,
    /** Название */
    NAME
}
