package ru.invest.api.dto.dto.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.dto.dto.bond.enums.RiskLevelDto;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BondDto {
    private String ticker;
    private String uid;
    private String isin;
    private String name;
    private PriceDto price;
    private CouponDto coupon;
    private RiskLevelDto riskLevel;
}
