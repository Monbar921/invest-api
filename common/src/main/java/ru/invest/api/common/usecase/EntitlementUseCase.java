package ru.invest.api.common.usecase;

import ru.invest.api.common.exception.GeneralPaymentRequiredException;
import ru.invest.api.common.model.account.PlanFeature;
import ru.invest.api.common.model.account.PlanLimit;

/**
 * Права пользователя по его тарифу: лимиты и фичи. Тариф - действующая подписка, а без неё (в том числе по истечении
 * срока) - тариф по умолчанию.
 */
public interface EntitlementUseCase {
    /**
     * Можно ли создать ещё один объект. Лимит проверяется только при создании: то, что уже есть сверх лимита
     * (например, после окончания платного тарифа), не удаляется и меняется как обычно.
     *
     * @param used сколько уже есть у пользователя
     * @throws GeneralPaymentRequiredException used не меньше лимита тарифа (402)
     */
    void requireBelowLimit(String userId, PlanLimit limit, long used);

    /**
     * @throws GeneralPaymentRequiredException фича не входит в тариф пользователя (402)
     */
    void requireFeature(String userId, PlanFeature feature);
}
