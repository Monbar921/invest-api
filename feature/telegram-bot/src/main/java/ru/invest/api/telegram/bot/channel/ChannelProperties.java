package ru.invest.api.telegram.bot.channel;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.rating.RatingScale;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Публичный Telegram-канал: автоматические посты о рейтинговых действиях, ключевой ставке и еженедельная подборка.
 * Публикует тот же бот, что шлёт уведомления: он должен быть администратором канала.
 */
@Data
@Component
@SuppressWarnings("checkstyle:MagicNumber")
@ConfigurationProperties(prefix = "ru.invest.api.telegram.channel")
public class ChannelProperties implements InitializingBean {
    /** false - посты не собираются и в канал ничего не уходит, даже накопленное в очереди */
    private boolean enabled;

    /** Канал: «@имя» публичного канала или числовой id «-100...» */
    private String chatId;

    /** Публичный адрес сайта без «/» в конце - для ссылок на страницы продукта: https://example.ru */
    private String siteUrl;

    /** Часовой пояс окна публикации и «дня» лимита */
    private ZoneId zone = ZoneId.of("Europe/Moscow");

    /** Больше постов в день не публикуется; остальные ждут следующего дня, пока не устареют */
    private int maxPostsPerDay = 5;

    /** Пауза между постами: десяток рейтинговых действий за день не выходит пачкой */
    private Duration minInterval = Duration.ofMinutes(30);

    /** Окно публикации по zone: с postingFrom включительно до postingTo - ночью посты не выходят */
    private LocalTime postingFrom = LocalTime.of(9, 0);
    private LocalTime postingTo = LocalTime.of(21, 0);

    /** Сколько событие актуально: старше - не собирается из ленты, неопубликованный пост - SKIPPED */
    private Duration maxAge = Duration.ofDays(2);

    /** Сколько хранить опубликованные и пропущенные посты */
    private Duration retention = Duration.ofDays(90);

    /** Собирать посты из ленты после каждой её загрузки; false - в тестах (сбор вызывается напрямую) */
    private boolean collectOnSync = true;

    /** Посты о рейтинговых действиях АКРА и НКР */
    private boolean ratingActions = true;

    /** Посты о решениях по ключевой ставке */
    private boolean keyRate = true;

    private WeeklyTop weeklyTop = new WeeklyTop();

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            return;
        }
        if (StringUtils.isBlank(chatId)) {
            throw new IllegalStateException("ru.invest.api.telegram.channel.chat-id is required when the channel is enabled");
        }
        if (StringUtils.isBlank(siteUrl) || !siteUrl.startsWith("https://") && !siteUrl.startsWith("http://")) {
            throw new IllegalStateException("ru.invest.api.telegram.channel.site-url must be an absolute http(s) URL");
        }
        if (maxPostsPerDay < 1 || !postingFrom.isBefore(postingTo)) {
            throw new IllegalStateException("ru.invest.api.telegram.channel: max-posts-per-day >= 1 "
                    + "and posting-from before posting-to");
        }
        weeklyTop.getGroups().forEach(group -> {
            if (StringUtils.isBlank(group.getTitle()) || !RatingScale.isGrade(group.getMin())
                    || !RatingScale.isGrade(group.getMax())) {
                throw new IllegalStateException("ru.invest.api.telegram.channel.weekly-top.groups: title, min and "
                        + "max grade are required, got " + group);
            }
        });
    }

    /** Адрес страницы продукта: «/bonds/X» → https://example.ru/bonds/X */
    public String page(final String path) {
        return (siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl) + path;
    }

    /** Еженедельная подборка самых доходных облигаций по группам рейтинга */
    @Data
    public static class WeeklyTop {
        private boolean enabled = true;

        /** Сколько облигаций в группе */
        private int perGroup = 3;

        /** Минимальный средний дневной оборот за 30 дней, ₽; null - без фильтра */
        private BigDecimal minTurnoverRub = BigDecimal.valueOf(1_000_000);

        /** Минимальный срок до погашения, лет: почти погашенные выпуски дают случайную доходность */
        private BigDecimal minYearsToMaturity = new BigDecimal("0.5");

        /** Группы риска по общей шкале рейтингов АКРА и НКР (самый низкий из рейтингов агентств) */
        private List<Group> groups = new ArrayList<>(List.of(
                new Group().setTitle("AAA – AA-").setMin("AA-").setMax("AAA"),
                new Group().setTitle("A+ – A-").setMin("A-").setMax("A+"),
                new Group().setTitle("BBB+ – BBB-").setMin("BBB-").setMax("BBB+"),
                new Group().setTitle("BB+ – BB-").setMin("BB-").setMax("BB+")));
    }

    @Data
    @NoArgsConstructor
    @Accessors(chain = true)
    public static class Group {
        private String title;
        /** худшая ступень группы */
        private String min;
        /** лучшая ступень группы */
        private String max;
    }
}
