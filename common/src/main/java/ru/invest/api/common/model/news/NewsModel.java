package ru.invest.api.common.model.news;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Внешняя новость. Время публикации - UTC, как и остальные даты в системе.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NewsModel {
    private Long id;
    private NewsSource source;
    /** id новости у источника: guid из RSS ЦБ, id новости Мосбиржи */
    private String externalId;
    private String title;
    private String summary;
    private String url;
    private LocalDateTime publishedAt;
    /** ISIN выпусков, упомянутых в новости */
    private Set<String> isins = Set.of();
    /** Эмитенты Мосбиржи, к которым относится новость */
    private Set<Long> emitterIds = Set.of();
    /** Текст новости для поиска эмитентов по названию; в БД не хранится */
    private String bodyForMatching;
    /** Облигации из системы, упомянутые в новости по ISIN - заполняется при чтении */
    private List<NewsBondModel> bonds = List.of();
    /** Названия эмитентов новости - заполняется при чтении */
    private List<String> issuers = List.of();
}
