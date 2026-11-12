package ru.invest.api.account.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.account.service.model.PlanModel;
import ru.invest.api.common.exception.GeneralPaymentRequiredException;
import ru.invest.api.common.model.account.PlanFeature;
import ru.invest.api.common.model.account.PlanLimit;
import ru.invest.api.common.usecase.EntitlementUseCase;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EntitlementUseCaseImpl implements EntitlementUseCase {
    /** Что ограничивает лимит - для сообщения: «не больше 50 правил уведомлений» */
    private static final Map<PlanLimit, String> LIMIT_NAMES = Map.of(
            PlanLimit.ALERTS, "правил уведомлений",
            PlanLimit.PORTFOLIO_POSITIONS, "покупок в портфеле");
    private static final Map<PlanFeature, String> FEATURE_NAMES = Map.of(
            PlanFeature.CALENDAR_ICS, "Подписка на календарь выплат");

    private final ActivePlanResolver resolver;

    @Override
    @Transactional(readOnly = true)
    public void requireBelowLimit(final String userId, final PlanLimit limit, final long used) {
        final PlanModel plan = resolver.resolve(userId).plan();
        final int max = plan.limits().get(limit);
        if (used < max) {
            return;
        }
        final String upgrade = resolver.plans().stream()
                .filter(other -> other.limits().get(limit) > max)
                .findFirst()
                .map(other -> " В тарифе «%s» - до %d.".formatted(other.title(), other.limits().get(limit)))
                .orElse("");
        throw GeneralPaymentRequiredException.limit("Тариф «%s»: не больше %d %s.%s"
                .formatted(plan.title(), max, LIMIT_NAMES.get(limit), upgrade), plan.id(), limit, max);
    }

    @Override
    @Transactional(readOnly = true)
    public void requireFeature(final String userId, final PlanFeature feature) {
        final PlanModel plan = resolver.resolve(userId).plan();
        if (plan.features().contains(feature)) {
            return;
        }
        final String upgrade = resolver.plans().stream()
                .filter(other -> other.features().contains(feature))
                .findFirst()
                .map(other -> " Доступно в тарифе «%s».".formatted(other.title()))
                .orElse("");
        throw GeneralPaymentRequiredException.feature("%s не входит в тариф «%s».%s"
                .formatted(Optional.ofNullable(FEATURE_NAMES.get(feature)).orElse(feature.name()), plan.title(),
                        upgrade), plan.id(), feature);
    }
}
