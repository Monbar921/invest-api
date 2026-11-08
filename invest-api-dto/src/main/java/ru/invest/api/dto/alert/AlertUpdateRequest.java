package ru.invest.api.dto.alert;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Изменение правила; незаданные поля не меняются.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class AlertUpdateRequest {
    /** Самое раннее предупреждение; на бэке то же ограничение - ru.invest.api.alerts.max-days */
    public static final int MAX_DAYS = 60;

    @Min(value = 1, message = "days must be from 1 to 60")
    @Max(value = MAX_DAYS, message = "days must be from 1 to 60")
    private Integer days;
    private AlertDirectionDto direction;
    private BigDecimal threshold;
    private Boolean enabled;
}
