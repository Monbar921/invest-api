package ru.invest.api.account.service.usecase;

import ru.invest.api.account.service.model.AccountPlanModel;
import ru.invest.api.account.service.model.CurrentSubscriptionModel;
import ru.invest.api.account.service.model.GrantPlanModel;
import ru.invest.api.account.service.model.PaidSubscriptionModel;
import ru.invest.api.account.service.model.PlanModel;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;

import java.time.Period;
import java.util.Optional;

/**
 * Тариф пользователя: просмотр, выдача и отзыв вручную и по оплате.
 */
public interface AccountPlanUseCase {
    /**
     * Действующий тариф, срок, лимиты с использованием и все тарифы для сравнения.
     */
    AccountPlanModel getPlan(String userId);

    /**
     * Выдать тариф с сегодняшнего момента. Действующая подписка отзывается: подписка у пользователя одна.
     * Автопродление отключается: оно продлевало бы тариф, заменённый вручную.
     *
     * @param grantedBy кто выдал (заголовок From)
     * @throws GeneralUnprocessableEntityException тариф неизвестен или это тариф по умолчанию, срок в прошлом (422)
     */
    AccountPlanModel grant(String userId, GrantPlanModel request, String grantedBy);

    /**
     * Отозвать действующую подписку: пользователь сразу на тарифе по умолчанию. Подписки нет - ничего не меняется.
     * Автопродление отключается.
     *
     * @param revokedBy кто отозвал (заголовок From)
     */
    void revoke(String userId, String revokedBy);

    /**
     * Тариф из конфигурации.
     */
    Optional<PlanModel> findPlan(String planId);

    /**
     * Действующий тариф и срок - без подсчёта лимитов.
     */
    CurrentSubscriptionModel current(String userId);

    /**
     * Выдать оплаченный тариф. Действует тот же тариф со сроком - срок продлевается от его последнего дня, иначе
     * (тариф по умолчанию, другой тариф, бессрочный) - от сегодняшнего: последний день = база + period. Действующая
     * подписка отзывается, как при ручной выдаче. Автопродление не трогает - им управляет оплата.
     *
     * @param paymentId платёж, которым оплачено
     */
    PaidSubscriptionModel grantPaid(String userId, String planId, Period period, long paymentId);

    /**
     * Отозвать подписку, выданную платежом (возврат денег), если она ещё действует. Иначе ничего не меняется.
     */
    void revokePaid(long subscriptionId);
}
