package ru.invest.api.dto.dto.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class MoneyDto {
    private BigDecimal quantity;
    private String currency;
}
