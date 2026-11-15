package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Средневзвешенная по стоимости величина.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioAnalyticsWeightedDto {
    /** Среднее по позициям, у которых величина есть; null - ни у одной нет */
    private BigDecimal value;
    /** Какая доля стоимости портфеля вошла в среднее, % */
    private BigDecimal coveredShare;
}
