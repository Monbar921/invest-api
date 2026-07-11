package ru.invest.api.common.model.parameters;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class BondSortModel {
    private BondSortField sortField;
    private BondSortOrder sortOrder = BondSortOrder.ASC;
}
