package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Сумма выплат в одной валюте.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioCalendarTotalDto {
    private String currency;
    /** Сумма известных выплат */
    private BigDecimal amount;
    /** amount по курсу на сегодня; null - курса нет */
    private BigDecimal amountInRub;
    /** Есть выплаты без объявленного размера: настоящая сумма больше amount */
    private boolean incomplete;
}
