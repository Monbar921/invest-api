package ru.invest.api.account.service.usecase;

import ru.invest.api.account.service.model.AccountPlanModel;
import ru.invest.api.account.service.model.GrantPlanModel;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;

/**
 * Тариф пользователя: просмотр, выдача и отзыв вручную (пока нет оплаты).
 */
public interface AccountPlanUseCase {
    /**
     * Действующий тариф, срок, лимиты с использованием и все тарифы для сравнения.
     */
    AccountPlanModel getPlan(String userId);

    /**
     * Выдать тариф с сегодняшнего момента. Действующая подписка отзывается: подписка у пользователя одна.
     *
     * @param grantedBy кто выдал (заголовок From)
     * @throws GeneralUnprocessableEntityException тариф неизвестен или это тариф по умолчанию, срок в прошлом (422)
     */
    AccountPlanModel grant(String userId, GrantPlanModel request, String grantedBy);

    /**
     * Отозвать действующую подписку: пользователь сразу на тарифе по умолчанию. Подписки нет - ничего не меняется.
     *
     * @param revokedBy кто отозвал (заголовок From)
     */
    void revoke(String userId, String revokedBy);
}
