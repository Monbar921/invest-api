package ru.invest.api.broker.service.model;

import java.time.LocalDate;

/**
 * Переходы к партнёру по облигации за день.
 */
public record BrokerClickStatModel(LocalDate date, String partnerId, String ticker, long clicks) {
}
