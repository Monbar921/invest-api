package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Календарь выплат портфеля за период: события, итоги по месяцам и за период.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioCalendarDto {
    private LocalDate from;
    private LocalDate to;
    /** По дате */
    private List<PortfolioCalendarEventDto> events;
    /** Только месяцы с событиями, по возрастанию */
    private List<PortfolioCalendarMonthDto> months;
    /** Итоги периода по валютам */
    private List<PortfolioCalendarTotalDto> totals;
    /** Итог периода в рублях по курсу на сегодня; null - у какой-то валюты нет курса */
    private BigDecimal amountInRub;
    /** Итог неполный: есть выплаты без размера или облигации, которых нет в системе */
    private boolean incomplete;
    /** Облигации портфеля, которых больше нет в системе: их выплат в календаре нет */
    private List<String> unknownTickers;
}
