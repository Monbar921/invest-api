package ru.invest.api.payment.service.provider;

import ru.invest.api.payment.service.provider.model.ChargeCommand;
import ru.invest.api.payment.service.provider.model.CreatePaymentCommand;
import ru.invest.api.payment.service.provider.model.ProviderPayment;

/**
 * Платёжный провайдер. Первая реализация - Т-Касса; другой провайдер - ещё одна реализация с другим id, остальной
 * код не меняется (как NotificationSender у уведомлений).
 */
public interface PaymentProvider {
    /** id в настройке ru.invest.api.payments.provider и в пути webhook */
    String id();

    /**
     * Платёж со страницей оплаты; карта сохраняется для автопродления.
     *
     * @throws PaymentProviderException провайдер отказал или не ответил
     */
    ProviderPayment create(CreatePaymentCommand command);

    /**
     * Списание по сохранённой карте. Провайдер не ответил после создания платежа - статус PENDING: итог придёт в
     * webhook или по запросу статуса.
     *
     * @throws PaymentProviderException платёж не создан - денег не списано
     */
    ProviderPayment charge(ChargeCommand command);

    /**
     * Текущее состояние платежа - если webhook не дошёл (локальный запуск без публичного адреса).
     *
     * @throws PaymentProviderException провайдер отказал или не ответил
     */
    ProviderPayment getState(String providerPaymentId);

    /**
     * Уведомление провайдера (webhook) с проверкой подписи.
     *
     * @throws InvalidNotificationException тело не разбирается или подпись не сходится
     */
    ProviderPayment parseNotification(String body);

    /** Тело ответа на принятый webhook: без него провайдер повторяет уведомление */
    String notificationResponse();
}
