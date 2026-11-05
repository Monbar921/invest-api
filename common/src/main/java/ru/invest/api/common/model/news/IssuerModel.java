package ru.invest.api.common.model.news;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Эмитент выпуска по данным Мосбиржи.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class IssuerModel {
    private String isin;
    private Long emitterId;
    /** Полное название: «Публичное акционерное общество "Селигдар"» */
    private String title;
    private String inn;
}
