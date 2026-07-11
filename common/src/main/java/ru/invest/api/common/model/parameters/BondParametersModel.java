package ru.invest.api.common.model.parameters;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.enums.RiskLevel;

import java.util.Comparator;
import java.util.List;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class BondParametersModel {
    private List<BondSortModel> bondSorts;
    private Comparator<BondModel> comparator;
    private ValueRangeModel currentPrice;
    private ValueRangeModel percentagePrice;
    private List<RiskLevel> riskLevels;
    private Boolean isOfz;
    private Integer batchLimit;
}
