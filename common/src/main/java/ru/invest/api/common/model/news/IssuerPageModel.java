package ru.invest.api.common.model.news;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.BondModel;

import java.util.List;

/**
 * Эмитент со своими выпусками из системы - для страницы эмитента.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class IssuerPageModel {
    /** id эмитента на Мосбирже */
    private Long id;
    /** Полное название: «Публичное акционерное общество "Селигдар"» */
    private String title;
    /** Название с сокращённой формой собственности: «ПАО "Селигдар"» */
    private String shortTitle;
    private String inn;
    /** Выпуски эмитента, которые есть в системе, по дате погашения */
    private List<BondModel> bonds;
}
