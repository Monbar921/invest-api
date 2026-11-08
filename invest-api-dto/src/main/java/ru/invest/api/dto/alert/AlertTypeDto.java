package ru.invest.api.dto.alert;

/**
 * Правило по дате события облигации: за days дней до купона, частичного погашения, оферты, погашения.
 */
public enum AlertTypeDto {
    COUPON_BEFORE,
    AMORTIZATION_BEFORE,
    OFFER_BEFORE,
    MATURITY_BEFORE
}
