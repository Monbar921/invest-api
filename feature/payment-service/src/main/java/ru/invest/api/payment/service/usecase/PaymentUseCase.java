package ru.invest.api.payment.service.usecase;

import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.payment.service.model.PaymentModel;
import ru.invest.api.payment.service.model.PaymentsInfoModel;
import ru.invest.api.payment.service.provider.InvalidNotificationException;
import ru.invest.api.payment.service.provider.PaymentProviderException;

import java.util.List;

/**
 * Покупка тарифа, история платежей, автопродление и webhook провайдера.
 */
public interface PaymentUseCase {
    /**
     * Включена ли оплата, ссылки на оферту и политику, действующее автопродление.
     */
    PaymentsInfoModel info(String userId);

    /**
     * Создать платёж за тариф: в ответе - страница оплаты провайдера. Тот же тариф со сроком продлевается от его
     * последнего дня, другой - начинается сегодня.
     *
     * @throws GeneralUnprocessableEntityException оплата выключена, тариф неизвестен или не продаётся (422)
     * @throws PaymentProviderException            провайдер отказал или не ответил (502)
     */
    PaymentModel purchase(String userId, String plan);

    /**
     * Платежи пользователя, новые первыми.
     */
    List<PaymentModel> history(String userId);

    /**
     * Платёж пользователя. Ждёт оплаты - статус сначала запрашивается у провайдера: так оплата доходит и без webhook.
     *
     * @throws GeneralNotFoundEntityException нет такого платежа у пользователя (404)
     */
    PaymentModel get(String userId, long paymentId);

    /**
     * Отключить автопродление: тариф действует до конца оплаченного срока, дальше - тариф по умолчанию. Нет
     * автопродления - ничего не меняется.
     */
    void cancelAutoRenewal(String userId);

    /**
     * Webhook провайдера: подпись проверяется, платёж применяется один раз.
     *
     * @return тело ответа провайдеру
     * @throws GeneralNotFoundEntityException провайдер не настроен (404)
     * @throws InvalidNotificationException   подпись не сходится (400)
     */
    String handleNotification(String provider, String body);
}
