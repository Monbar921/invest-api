package ru.invest.api.dto.bond.issuer;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.dto.bond.BondDto;

import java.util.List;

/**
 * Эмитент облигаций по данным Мосбиржи с его выпусками из системы.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class IssuerDto {
    /** id эмитента на Мосбирже */
    private Long id;
    /** Полное название: «Публичное акционерное общество "Селигдар"»; null - Мосбиржа его не дала */
    private String title;
    /** Название с сокращённой формой собственности: «ПАО "Селигдар"» */
    private String shortTitle;
    private String inn;
    /** Выпуски эмитента из системы по дате погашения, без графика выплат */
    private List<BondDto> bonds;
}
