package ru.invest.api.dto.bond.issuer;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Эмитент выпуска - для ссылки на страницу эмитента.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class IssuerShortDto {
    /** id эмитента на Мосбирже */
    private Long id;
    /** Название с сокращённой формой собственности: «ПАО "Селигдар"» */
    private String shortTitle;
}
