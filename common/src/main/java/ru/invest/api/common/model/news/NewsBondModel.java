package ru.invest.api.common.model.news;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Облигация, к которой относится новость - для ссылки на её карточку.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class NewsBondModel {
    private String ticker;
    private String name;
}
