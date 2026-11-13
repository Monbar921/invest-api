package ru.invest.api.dto.account;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Платёж за тариф.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PaymentDto {
    private Long id;
    /** id тарифа */
    private String plan;
    private String planTitle;
    /** Оплаченный срок, ISO-8601: P1M */
    private String period;
    /** Срок по-русски: «1 месяц» */
    private String periodTitle;
    /** Рубли */
    private BigDecimal amount;
    /** PURCHASE - покупка, RENEWAL - автопродление */
    private String kind;
    /** PENDING - ждёт оплаты, SUCCEEDED - оплачен, FAILED - не прошёл, REFUNDED - возвращён */
    private String status;
    /** Маска карты */
    private String cardPan;
    /** Почему не прошёл: сообщение провайдера */
    private String errorMessage;
    /** Страница оплаты провайдера: только пока покупка ждёт оплаты */
    private String confirmationUrl;
    private ZonedDateTime createdAt;
    /** null - не оплачен */
    private ZonedDateTime paidAt;
}
