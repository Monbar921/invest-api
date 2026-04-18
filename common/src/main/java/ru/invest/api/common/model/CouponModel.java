package ru.invest.api.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CouponModel {
    private String uid;
    private int quantityPerYear;
    private List<CouponDataModel> couponData;
}
