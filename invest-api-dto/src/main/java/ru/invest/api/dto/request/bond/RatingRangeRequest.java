package ru.invest.api.dto.request.bond;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Диапазон кредитного рейтинга по общей шкале включительно: min - худшая допустимая ступень, max - лучшая.
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RatingRangeRequest {
    private static final String GRADE = "AAA|AA[+-]?|A[+-]?|BBB[+-]?|BB[+-]?|B[+-]?|CCC|CC|C|SD|RD|D";

    @Pattern(regexp = GRADE, message = "unknown rating grade")
    private String min;
    @Pattern(regexp = GRADE, message = "unknown rating grade")
    private String max;
}
