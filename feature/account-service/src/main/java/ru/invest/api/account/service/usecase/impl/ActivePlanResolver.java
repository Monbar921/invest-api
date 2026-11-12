package ru.invest.api.account.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.invest.api.account.service.config.PlanProperties;
import ru.invest.api.account.service.model.PlanModel;
import ru.invest.api.common.entity.AccountSubscription;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.account.PlanFeature;
import ru.invest.api.common.model.account.PlanLimit;
import ru.invest.api.common.repository.AccountSubscriptionRepository;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Действующий тариф пользователя. Срок проверяется при каждом запросе, поэтому истёкшая подписка сразу перестаёт
 * действовать - шедулер не нужен.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class ActivePlanResolver {
    private final PlanProperties properties;
    private final AccountSubscriptionRepository repository;
    private final DateTimeMapper dateTimeMapper;

    /**
     * Действующая подписка, а без неё - тариф по умолчанию. Тариф подписки убрали из конфигурации - тоже тариф по
     * умолчанию (и предупреждение в логе): лучше урезать права, чем упасть.
     */
    ActivePlan resolve(final String userId) {
        final Optional<AccountSubscription> subscription = repository.findActive(userId, dateTimeMapper.getNow())
                .stream()
                .findFirst();
        if (subscription.isPresent()) {
            final String planId = subscription.get().getPlan();
            if (properties.getPlans().containsKey(planId)) {
                return new ActivePlan(plan(planId), subscription.get());
            }
            log.warn("Subscription {} of user {} has plan {} which is not configured, default plan is used",
                    subscription.get().getId(), userId, planId);
        }
        return new ActivePlan(plan(properties.getDefaultPlan()), null);
    }

    /** Тарифы в порядке настройки */
    List<PlanModel> plans() {
        return properties.getPlans().keySet().stream().map(this::plan).toList();
    }

    PlanModel plan(final String id) {
        final PlanProperties.Plan plan = properties.getPlans().get(id);
        final Map<PlanLimit, Integer> limits = new EnumMap<>(PlanLimit.class);
        limits.putAll(plan.getLimits());
        final Set<PlanFeature> features = EnumSet.noneOf(PlanFeature.class);
        features.addAll(plan.getFeatures());
        return new PlanModel(id, plan.getTitle(), plan.getDescription(), plan.isPaid(), limits, features);
    }

    /**
     * @param subscription действующая подписка; null - тариф по умолчанию
     */
    record ActivePlan(PlanModel plan, AccountSubscription subscription) {
    }
}
