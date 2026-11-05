package ru.invest.api.common.model.event;

/**
 * Событие по облигации из её собственных данных - графика купонов, амортизации, дат оферты и погашения.
 */
public enum BondEventType {
    COUPON,
    AMORTIZATION,
    OFFER,
    MATURITY
}
