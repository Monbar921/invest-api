package ru.invest.api.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PriceModel {
    private String uid;
    private MoneyModel nominal;
    private MoneyModel current;
    private BigDecimal percentagePrice;
}
