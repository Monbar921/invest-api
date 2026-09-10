package ru.invest.api.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CouponDataModel {
    private Long id;
    private String ticker;
    private MoneyModel price;
    private LocalDateTime fixDate;
    private LocalDateTime paymentDate;
}
