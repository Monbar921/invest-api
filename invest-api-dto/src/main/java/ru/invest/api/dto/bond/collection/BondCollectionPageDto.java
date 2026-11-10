package ru.invest.api.dto.bond.collection;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.page.PageDto;

/**
 * Подборка облигаций со страницей выпусков.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BondCollectionPageDto {
    private String slug;
    private String title;
    private String description;
    private PageDto<BondDto> bonds;
}
