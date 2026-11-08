package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Итог портфеля в одной валюте номинала; позиции без текущей цены не входят.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioTotalDto {
    private String currency;
    private BigDecimal buyAmount;
    private BigDecimal currentAmount;
    private BigDecimal revenue;
    /** null - стоимость покупки 0 */
    private BigDecimal revenuePercent;
}
