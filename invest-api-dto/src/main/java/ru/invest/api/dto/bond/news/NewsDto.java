package ru.invest.api.dto.bond.news;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Внешняя новость. Текст не перепечатывается: заголовок, короткий анонс (есть не у всех источников) и ссылка.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NewsDto {
    private Long id;
    private NewsSourceDto source;
    private String title;
    private String summary;
    private String url;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime publishedAt;
    /** Облигации из системы, упомянутые в новости по ISIN */
    private List<NewsBondDto> bonds;
    /** Эмитенты, к которым относится новость */
    private List<String> issuers;
}
