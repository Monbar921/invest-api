package ru.invest.api.common.model.parameters;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class BondParametersModel {
    private List<BondSortModel> bondSorts;
    private Integer batchLimit;
}
