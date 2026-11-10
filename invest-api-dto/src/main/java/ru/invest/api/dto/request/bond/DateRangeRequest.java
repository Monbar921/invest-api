package ru.invest.api.dto.request.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Диапазон дат включительно (YYYY-MM-DD); любая граница может быть не задана.
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class DateRangeRequest {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
}
