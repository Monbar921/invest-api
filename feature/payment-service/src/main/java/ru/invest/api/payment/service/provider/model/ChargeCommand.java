package ru.invest.api.payment.service.provider.model;

import java.math.BigDecimal;

/**
 * Списание по сохранённой карте.
 *
 * @param orderId  наш номер заказа, уникальный
 * @param amount   рубли
 * @param rebillId токен сохранённой карты у провайдера
 */
public record ChargeCommand(String orderId, BigDecimal amount, String description, String rebillId,
                            String notificationUrl) {
}
