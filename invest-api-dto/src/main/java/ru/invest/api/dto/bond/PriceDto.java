package ru.invest.api.dto.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PriceDto {
    private String uid;
    private MoneyDto nominal;
    private MoneyDto current;
    private BigDecimal percentagePrice;
}
