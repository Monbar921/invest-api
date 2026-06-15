package ru.invest.api.common.model.parameters;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BondSortModel {
    private BondSortField sortField;
    private BondSortOrder sortOrder = BondSortOrder.ASC;
}
