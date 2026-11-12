package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * Выплаты за месяц.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioCalendarMonthDto {
    /** YYYY-MM */
    private String month;
    private List<PortfolioCalendarTotalDto> totals;
    /** Все валюты в рублях по курсу на сегодня; null - у какой-то валюты нет курса */
    private BigDecimal amountInRub;
    private boolean incomplete;
}
