package ru.invest.api.dto.bond.event;

/**
 * Событие облигации: купон, частичное погашение номинала, оферта, погашение.
 */
public enum BondEventTypeDto {
    COUPON,
    AMORTIZATION,
    OFFER,
    MATURITY
}
