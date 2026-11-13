package ru.invest.api.account.service.model;

import java.time.LocalDateTime;

/**
 * Действующий тариф пользователя - для автопродления.
 *
 * @param plan        id действующего тарифа
 * @param defaultPlan подписки нет или она закончилась
 * @param validUntil  UTC, не включительно; null - бессрочно или тариф по умолчанию
 */
public record CurrentSubscriptionModel(String plan, boolean defaultPlan, LocalDateTime validUntil) {
}
