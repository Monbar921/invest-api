package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Изменение стоимости при сдвиге доходности: ΔV ≈ −Dmod × Δy × V, только облигации с постоянным купоном и дюрацией.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioRateSensitivityDto {
    /** Сдвиг доходности, п.п. */
    private BigDecimal shiftPercent;
    /** Изменение стоимости в рублях при росте доходности (≤ 0) */
    private BigDecimal valueChangeUp;
    /** Изменение стоимости в рублях при снижении доходности (≥ 0) */
    private BigDecimal valueChangeDown;
    /** |Изменение| в % к стоимости учтённых облигаций */
    private BigDecimal changePercent;
    /** Какая доля стоимости портфеля учтена, % */
    private BigDecimal coveredShare;
}
