package ru.invest.api.dto.bond.news;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Облигация, упомянутая в новости, - для ссылки на карточку.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NewsBondDto {
    private String ticker;
    private String name;
}
