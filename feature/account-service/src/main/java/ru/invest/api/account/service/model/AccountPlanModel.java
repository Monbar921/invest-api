package ru.invest.api.account.service.model;

import ru.invest.api.common.model.account.SubscriptionSource;

import java.time.LocalDate;
import java.util.List;

/**
 * Тариф пользователя.
 *
 * @param plan        действующий тариф
 * @param defaultPlan тариф по умолчанию: подписки нет или она закончилась
 * @param source      откуда подписка; null - тариф по умолчанию
 * @param validUntil  последний день подписки включительно (в поясе тарифов); null - бессрочно или тариф по умолчанию
 * @param limits      лимиты действующего тарифа и сколько использовано, в порядке PlanLimit
 * @param plans       все тарифы - для сравнения
 */
public record AccountPlanModel(PlanModel plan, boolean defaultPlan, SubscriptionSource source, LocalDate validUntil,
                               List<LimitUsageModel> limits, List<PlanModel> plans) {
}
