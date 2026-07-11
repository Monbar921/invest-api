package ru.invest.api.dto.request.bond;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class ValueRangeRequest {
    private BigDecimal min;
    private BigDecimal max;
}
