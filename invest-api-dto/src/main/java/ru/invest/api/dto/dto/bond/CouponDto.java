package ru.invest.api.dto.dto.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CouponDto {
    private String uid;
    private int quantityPerYear;
    private BigDecimal interest;
    private List<CouponDataDto> couponData;
}
