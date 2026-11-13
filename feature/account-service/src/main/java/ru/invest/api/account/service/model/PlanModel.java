package ru.invest.api.account.service.model;

import ru.invest.api.common.model.account.PlanFeature;
import ru.invest.api.common.model.account.PlanLimit;

import java.math.BigDecimal;
import java.time.Period;
import java.util.Map;
import java.util.Set;

/**
 * Тариф из конфигурации.
 *
 * @param limits   лимит - сколько можно создать
 * @param features включённые фичи
 * @param price    цена за period, рубли; null - тариф не продаётся
 * @param period   сколько даёт одна оплата; null - тариф не продаётся
 */
public record PlanModel(String id, String title, String description, boolean paid, Map<PlanLimit, Integer> limits,
                        Set<PlanFeature> features, BigDecimal price, Period period) {
    /** Можно купить: есть цена и срок */
    public boolean purchasable() {
        return price != null && period != null;
    }
}
