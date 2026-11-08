package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioDto {
    /** По тикеру, затем по дате покупки */
    private List<PortfolioPositionDto> positions;
    /** По одному итогу на валюту номинала */
    private List<PortfolioTotalDto> totals;
}
