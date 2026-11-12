package ru.invest.api.account.service.model;

import ru.invest.api.common.model.account.PlanFeature;
import ru.invest.api.common.model.account.PlanLimit;

import java.util.Map;
import java.util.Set;

/**
 * Тариф из конфигурации.
 *
 * @param limits   лимит - сколько можно создать
 * @param features включённые фичи
 */
public record PlanModel(String id, String title, String description, boolean paid, Map<PlanLimit, Integer> limits,
                        Set<PlanFeature> features) {
}
