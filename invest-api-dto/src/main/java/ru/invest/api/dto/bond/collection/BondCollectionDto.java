package ru.invest.api.dto.bond.collection;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Подборка облигаций на готовом фильтре.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BondCollectionDto {
    /** Часть адреса: /collections/{slug} */
    private String slug;
    private String title;
    private String description;
}
