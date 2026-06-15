package ru.invest.api.dto.request.bond;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BondSortRequest {
    @NotNull
    private BondSortFieldRequest sortField;
    private BondSortOrderRequest sortOrder = BondSortOrderRequest.ASC;
}
