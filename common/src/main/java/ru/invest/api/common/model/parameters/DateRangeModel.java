package ru.invest.api.common.model.parameters;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * Диапазон дат включительно; границы необязательны.
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class DateRangeModel {
    private LocalDate from;
    private LocalDate to;
}
