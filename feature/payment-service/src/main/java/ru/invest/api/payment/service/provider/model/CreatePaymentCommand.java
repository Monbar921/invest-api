package ru.invest.api.payment.service.provider.model;

import java.math.BigDecimal;

/**
 * Платёж со страницей оплаты.
 *
 * @param orderId     наш номер заказа, уникальный
 * @param amount      рубли
 * @param description что покупается - покажется на странице оплаты
 * @param customerKey пользователь у провайдера: к нему привязывается сохранённая карта
 */
public record CreatePaymentCommand(String orderId, BigDecimal amount, String description, String customerKey,
                                   String successUrl, String failUrl, String notificationUrl) {
}
