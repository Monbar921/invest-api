package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;

/**
 * Изменение стоимости при параллельном сдвиге доходности: ΔV ≈ −Dmod × Δy × V. Считается по облигациям с
 * постоянным купоном и модифицированной дюрацией.
 *
 * @param shiftPercent   сдвиг доходности, п.п.
 * @param valueChangeUp  изменение стоимости в рублях при росте доходности на shiftPercent (≤ 0)
 * @param valueChangeDown изменение стоимости в рублях при снижении доходности на shiftPercent (≥ 0)
 * @param changePercent  |изменение| в % к стоимости учтённых облигаций
 * @param coveredShare   какая доля стоимости портфеля учтена, %
 */
public record RateSensitivityModel(BigDecimal shiftPercent,
                                   BigDecimal valueChangeUp,
                                   BigDecimal valueChangeDown,
                                   BigDecimal changePercent,
                                   BigDecimal coveredShare) {
}
