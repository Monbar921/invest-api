package ru.invest.api.payment.service.model;

import ru.invest.api.common.model.payment.PaymentKind;
import ru.invest.api.common.model.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Period;

/**
 * Платёж пользователя.
 *
 * @param planTitle       название тарифа; тариф убрали из конфигурации - его id
 * @param amount          рубли
 * @param confirmationUrl страница оплаты: только пока покупка ждёт оплаты
 * @param createdAt       UTC
 * @param paidAt          UTC; null - не оплачен
 */
public record PaymentModel(long id, String plan, String planTitle, Period period, BigDecimal amount, PaymentKind kind,
                           PaymentStatus status, String cardPan, String errorMessage, String confirmationUrl,
                           LocalDateTime createdAt, LocalDateTime paidAt) {
}
