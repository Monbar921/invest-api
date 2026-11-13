package ru.invest.api.payment.service.provider.model;

import ru.invest.api.common.model.payment.PaymentStatus;

import java.math.BigDecimal;

/**
 * Состояние платежа у провайдера: ответ на создание, списание, запрос статуса или webhook.
 *
 * @param orderId           наш номер заказа; null - провайдер его не вернул
 * @param providerPaymentId id платежа у провайдера
 * @param status            статус провайдера, сведённый к нашему
 * @param providerStatus    статус провайдера как есть
 * @param amount            рубли; null - провайдер не вернул сумму
 * @param confirmationUrl   страница оплаты; только у создания
 * @param rebillId          токен сохранённой карты; приходит в webhook успешной оплаты с сохранением карты
 * @param cardPan           маска карты
 * @param errorMessage      почему не прошёл
 */
public record ProviderPayment(String orderId, String providerPaymentId, PaymentStatus status, String providerStatus,
                              BigDecimal amount, String confirmationUrl, String rebillId, String cardPan,
                              String errorMessage) {
}
