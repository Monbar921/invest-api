package ru.invest.api.dto.request.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import ru.invest.api.dto.bond.enums.RiskLevelDto;

import java.util.List;

@Data
@NoArgsConstructor
public class BondParametersRequest {
    private List<BondSortRequest> bondSorts;
    /**
     * Диапазон текущей цены в рублях: цена облигаций в иностранной валюте переводится в рубли по курсу ЦБ.
     * Облигации, для которых цены или курса нет, фильтр не отсекает, но они идут в конце выдачи.
     */
    private ValueRangeRequest currentPrice;
    /**
     * Диапазон текущей цены в процентах от номинала - не зависит от валюты облигации.
     * Облигации без текущей цены фильтр не отсекает, но они идут в конце выдачи.
     */
    private ValueRangeRequest percentagePrice;
    private List<RiskLevelDto> riskLevels;
    private Boolean isOfz;
}
