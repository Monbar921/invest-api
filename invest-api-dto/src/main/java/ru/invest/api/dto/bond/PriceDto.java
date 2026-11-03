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
    /** Текущая цена в рублях по курсу ЦБ, до копеек; null - цены или курса нет. По ней идёт сортировка PRICE */
    private BigDecimal currentInRub;
    private BigDecimal percentagePrice;
    /** НКД на одну облигацию */
    private MoneyDto aci;
}
