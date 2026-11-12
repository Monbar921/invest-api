package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;

/**
 * Сумма выплат в одной валюте - выплаты в разных валютах не складываются.
 *
 * @param amount      сумма известных выплат
 * @param amountInRub amount по курсу на сегодня; null - курса нет
 * @param incomplete  есть выплаты без объявленного размера: настоящая сумма больше amount
 */
public record CalendarTotalModel(String currency, BigDecimal amount, BigDecimal amountInRub, boolean incomplete) {
}
