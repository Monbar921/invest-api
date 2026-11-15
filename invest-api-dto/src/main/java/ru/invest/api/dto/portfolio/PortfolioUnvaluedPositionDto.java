package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Позиция, которую нельзя оценить в рублях: в долях её нет.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioUnvaluedPositionDto {
    private String ticker;
    private String bondName;
    /** UNKNOWN_BOND - облигации нет в системе, NO_PRICE - нет цены, NO_RATE - нет курса валюты */
    private String reason;
}
