package ru.invest.api.dto.request.bond;

/**
 * Поля, по которым можно сортировать список облигаций.
 */
public enum BondSortFieldRequest {
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
