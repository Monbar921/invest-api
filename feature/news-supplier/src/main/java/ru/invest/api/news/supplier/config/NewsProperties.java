package ru.invest.api.news.supplier.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.news.NewsSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
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

    /**
     * Кредитные рейтинги из релизов агентств.
     */
    private Ratings ratings = new Ratings();

    @Data
    @NoArgsConstructor
    public static class Ratings {
        private static final int DEFAULT_MAX_AGE_MONTHS = 15;
        private static final int DEFAULT_MAX_PAGES = 300;
        private static final long DEFAULT_PAGE_PAUSE_MILLIS = 300;

        /**
         * Рейтинг без нового действия дольше этого срока не используется: агентства пересматривают рейтинг минимум
         * раз в год, рейтинг старше - вероятно отозван, а отзыв мы пропустили.
         */
        private Period maxAge = Period.ofMonths(DEFAULT_MAX_AGE_MONTHS);
        /**
         * Загрузить историю релизов за maxAge, если рейтингов в БД ещё нет (первый запуск).
         */
        private boolean historyOnEmpty = true;
        /**
         * Сколько страниц списка АКРА (по 10 релизов) читать за раз - защита от бесконечного обхода.
         */
        private int historyMaxPages = DEFAULT_MAX_PAGES;
        /**
         * Пауза между страницами АКРА - не нагружать сайт агентства.
         */
        private Duration historyPagePause = Duration.ofMillis(DEFAULT_PAGE_PAUSE_MILLIS);
    }

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
