package ru.invest.api.payment.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Оплата для страницы «Тариф».
 *
 * @param enabled     можно купить тариф
 * @param offerUrl    оферта
 * @param privacyUrl  политика обработки персональных данных
 * @param autoRenewal действующее автопродление; null - нет
 */
public record PaymentsInfoModel(boolean enabled, String offerUrl, String privacyUrl, AutoRenewal autoRenewal) {
    /**
     * @param nextChargeDate день следующего списания (в поясе тарифов)
     * @param amount         сколько спишется, рубли; null - тариф больше не продаётся
     */
    public record AutoRenewal(String plan, String planTitle, String cardPan, LocalDate nextChargeDate,
                              BigDecimal amount) {
    }
}
