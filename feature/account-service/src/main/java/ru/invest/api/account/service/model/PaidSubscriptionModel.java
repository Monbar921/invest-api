package ru.invest.api.account.service.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Подписка, выданная оплатой.
 *
 * @param subscriptionId id в account_subscription
 * @param validUntil     UTC, не включительно
 * @param lastDay        последний день включительно (в поясе тарифов)
 */
public record PaidSubscriptionModel(long subscriptionId, LocalDateTime validUntil, LocalDate lastDay) {
}
