package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Выплаты за месяц.
 *
 * @param month       YYYY-MM
 * @param totals      по валютам
 * @param amountInRub все валюты в рублях по курсу на сегодня; null - у какой-то валюты нет курса
 * @param incomplete  в месяце есть выплаты без объявленного размера
 */
public record CalendarMonthModel(String month,
                                 List<CalendarTotalModel> totals,
                                 BigDecimal amountInRub,
                                 boolean incomplete) {
}
