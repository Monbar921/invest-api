package ru.invest.api.dto.dto.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CouponDataDto {
    private MoneyDto price;
    private LocalDateTime fixDate;
    private LocalDateTime paymentDate;
}
