package ru.invest.api.common.model.parameters;

import lombok.Data;
import lombok.NoArgsConstructor;
import ru.invest.api.common.model.enums.RiskLevel;

import java.util.List;

@Data
@NoArgsConstructor
public class BondParametersRequestModel {
    private List<BondSortModel> bondSorts;
    private ValueRangeModel currentPrice;
    private ValueRangeModel percentagePrice;
    private List<RiskLevel> riskLevels;
    private Boolean isOfz;
    private Boolean isFixedCoupon;
    /** Дата погашения в диапазоне; облигация без даты погашения при заданном фильтре не подходит */
    private DateRangeModel maturityDate;
    private Integer batchLimit;
}
