package ru.invest.api.common.model.parameters;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Диапазон ступеней общей шкалы рейтингов включительно («BBB-».. «AAA»); границы необязательны.
 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RatingRangeModel {
    /** худшая допустимая ступень */
    private String min;
    /** лучшая допустимая ступень */
    private String max;
}
