package ru.invest.api.dto.request.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import ru.invest.api.dto.bond.enums.RiskLevelDto;

import java.util.List;

@Data
@NoArgsConstructor
public class BondParametersRequest {
    private List<BondSortRequest> bondSorts;
    private ValueRangeRequest currentPrice;
    private ValueRangeRequest percentagePrice;
    private List<RiskLevelDto> riskLevels;
    private Boolean isOfz;
}
