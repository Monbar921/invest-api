package ru.invest.api.dto.bond.sitemap;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * Что перечислить в sitemap.xml: адреса строит gateway.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class SitemapDto {
    /** Тикеры всех облигаций - /bonds/{ticker} */
    private List<String> tickers;
    /** id эмитентов, у которых есть выпуски в системе, - /issuers/{id} */
    private List<Long> issuerIds;
    /** Подборки - /collections/{slug} */
    private List<String> collections;
}
