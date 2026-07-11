package ru.invest.api.common.model.parameters;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class PriceRequestModel {
    private BigDecimal min;
    private BigDecimal max;
}
