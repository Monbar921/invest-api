package ru.invest.api.account.service.model;

import ru.invest.api.common.model.account.PlanLimit;

/**
 * Лимит тарифа и его использование. used может быть больше max: то, что создано на прошлом тарифе, не удаляется.
 */
public record LimitUsageModel(PlanLimit limit, int max, long used) {
}
