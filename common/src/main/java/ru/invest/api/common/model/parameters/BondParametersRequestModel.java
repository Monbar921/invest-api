package ru.invest.api.common.model.parameters;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class BondParametersRequestModel {
    private List<BondSortModel> bondSorts;
    private PriceRequestModel price;
    private Integer batchLimit;
}
