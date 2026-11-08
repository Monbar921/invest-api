package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Весь портфель в рублях: сумма рублёвых сумм итогов по всем валютам.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioSummaryDto {
    private BigDecimal buyAmountInRub;
    private BigDecimal currentAmountInRub;
    private BigDecimal revenueInRub;
    /** Доход в рублях в % к стоимости покупки в рублях */
    private BigDecimal revenuePercent;
}
