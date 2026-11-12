package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Календарь выплат портфеля за период.
 *
 * @param events         события по дате
 * @param months         только месяцы с событиями, по возрастанию
 * @param totals         итоги периода по валютам
 * @param amountInRub    итог периода в рублях по курсу на сегодня; null - у какой-то валюты нет курса
 * @param incomplete     итог неполный: есть выплаты без размера или облигации, которых нет в системе
 * @param unknownTickers облигации портфеля, которых больше нет в системе: их выплат в календаре нет
 */
public record PortfolioCalendarModel(LocalDate from,
                                     LocalDate to,
                                     List<CalendarEventModel> events,
                                     List<CalendarMonthModel> months,
                                     List<CalendarTotalModel> totals,
                                     BigDecimal amountInRub,
                                     boolean incomplete,
                                     List<String> unknownTickers) {
}
