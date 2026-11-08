package ru.invest.api.dto.alert;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

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
    @NotNull(message = "days is required")
    @Min(value = 1, message = "days must be from 1 to 60")
    @Max(value = MAX_DAYS, message = "days must be from 1 to 60")
    private Integer days;
}
