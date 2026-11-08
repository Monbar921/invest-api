package ru.invest.api.dto.alert;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class AlertCreateRequest {
    /** Самое раннее предупреждение; на бэке то же ограничение - ru.invest.api.alerts.max-days */
    public static final int MAX_DAYS = 60;

    @NotBlank(message = "ticker is required")
    private String ticker;
    @NotNull(message = "type is required")
    private AlertTypeDto type;
    /** Для правил по датам */
    @Min(value = 1, message = "days must be from 1 to 60")
    @Max(value = MAX_DAYS, message = "days must be from 1 to 60")
    private Integer days;
    /** Для правил по цене и доходности */
    private AlertDirectionDto direction;
    /** Для правил по цене и доходности; пределы зависят от типа (проверяет invest-api, иначе 422) */
    private BigDecimal threshold;
}
