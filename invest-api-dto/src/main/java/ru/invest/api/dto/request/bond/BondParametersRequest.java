package ru.invest.api.dto.request.bond;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class BondParametersRequest {
    private List<BondSortRequest> bondSorts;
    private PriceRequest price;
}
