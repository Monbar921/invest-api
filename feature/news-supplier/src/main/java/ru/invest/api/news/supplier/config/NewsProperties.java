package ru.invest.api.news.supplier.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.news.NewsSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Источники ленты новостей помимо ЦБ и Мосбиржи.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ru.invest.api.news")
public class NewsProperties {
    /**
     * RSS-ленты СМИ.
     */
    private List<Feed> feeds = new ArrayList<>();

    /**
     * Списки рейтинговых действий АКРА и НКР; null - агентство не загружается.
     */
    private String acraUrl;
    private String nkrUrl;

    /**
     * Даты заседаний Совета директоров Банка России по ключевой ставке (дни решения). ЦБ публикует календарь на год
     * вперёд, API для него нет - список дополняется вручную раз в год.
     */
    private List<LocalDate> keyRateMeetings = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Feed {
        private NewsSource source;
        private String url;
        /**
         * Общая лента (политика, спорт, происшествия): сохраняются только новости, где нашёлся выпуск или эмитент
         * из системы. false - деловая лента, сохраняется целиком.
         */
        private boolean onlyMatched;
    }
}
