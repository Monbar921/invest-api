package ru.invest.api.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BondModel {
    private String ticker;
    private String uid;
    private String isin;
    private String name;
    private PriceModel price;
    private CouponModel coupon;
}
