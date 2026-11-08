package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioDto {
    /** Покупки одной облигации - одной строкой, по тикеру */
    private List<PortfolioGroupDto> groups;
    /** По одному итогу на валюту номинала */
    private List<PortfolioTotalDto> totals;
    /** Весь портфель в рублях; null - портфель пуст или у какой-то валюты нет курса */
    private PortfolioSummaryDto summary;
}
