package ru.invest.api.feature.telegram;

import org.junit.jupiter.api.Test;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.telegram.bot.channel.ChannelProperties;
import ru.invest.api.telegram.bot.channel.RatingActionClassifier;
import ru.invest.api.telegram.bot.channel.RatingActionClassifier.Change;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Без Spring и БД: какие рейтинговые действия идут в канал (заголовки - настоящие формулировки АКРА и НКР) и проверка
 * настроек канала при старте.
 */
class TelegramChannelRulesTest {

    @Test
    void ratingChangesArePosted() {
        assertThat(classify(NewsSource.ACRA, "АКРА понизило кредитный рейтинг АО «Авто Финанс Банк» до BBB+(RU)"),
                equalTo(Optional.of(Change.DOWNGRADED)));
        assertThat(classify(NewsSource.NKR, "НКР понизило кредитный рейтинг ПАО «Авто Финанс Банк» с A+.ru до A.ru, "
                + "прогноз — негативный"), equalTo(Optional.of(Change.DOWNGRADED)));
        assertThat(classify(NewsSource.NKR, "НКР повысила кредитный рейтинг ООО «Сэтл Групп» до A.ru, прогноз стабильный"),
                equalTo(Optional.of(Change.UPGRADED)));
        // заглавными, как в списке АКРА, с русскими буквами в «ВВВ»
        assertThat(classify(NewsSource.ACRA,
                        "АКРА ПРИСВОИЛО ВЫПУСКУ ОБЛИГАЦИЙ АО «ПУШКИНСКОЕ ПЗ» (RU000A1080Y2) КРЕДИТНЫЙ РЕЙТИНГ ВВВ-(RU)"),
                equalTo(Optional.of(Change.ASSIGNED)));
        assertThat(classify(NewsSource.ACRA, "АКРА отозвало кредитный рейтинг ООО «Ромашка»"),
                equalTo(Optional.of(Change.WITHDRAWN)));
    }

    @Test
    void affirmationsOutlooksAndOtherScalesAreNotPosted() {
        assertThat(classify(NewsSource.ACRA, "АКРА подтвердило кредитный рейтинг АО «Авто Финанс Банк» на уровне A(RU), "
                + "прогноз «Стабильный»"), equalTo(Optional.empty()));
        assertThat(classify(NewsSource.NKR, "НКР повысила прогноз по кредитному рейтингу ПАО «Банк» A.ru до позитивного"),
                equalTo(Optional.empty()));
        assertThat(classify(NewsSource.ACRA, "АКРА подтвердило рейтинг AA(RU) и понизило прогноз до «Негативного»"),
                equalTo(Optional.empty()));
        // международная шкала и ожидаемый рейтинг - не национальная шкала
        assertThat(classify(NewsSource.ACRA, "АКРА повысило кредитный рейтинг Республики Беларусь по международной шкале до B+"),
                equalTo(Optional.empty()));
        assertThat(classify(NewsSource.ACRA, "АКРА присвоило ожидаемый кредитный рейтинг eAA-(RU) выпуску облигаций"),
                equalTo(Optional.empty()));
        // не рейтинговое агентство и пустой заголовок
        assertThat(classify(NewsSource.INTERFAX, "АКРА понизило кредитный рейтинг АО «Авто Финанс Банк» до BBB+(RU)"),
                equalTo(Optional.empty()));
        assertThat(classify(NewsSource.NKR, null), equalTo(Optional.empty()));
    }

    @Test
    void enabledChannelRequiresChatAndSite() {
        assertDoesNotThrow(new ChannelProperties()::afterPropertiesSet);
        assertDoesNotThrow(enabled("@channel", "https://invest.test")::afterPropertiesSet);

        assertThrows(IllegalStateException.class, enabled(null, "https://invest.test")::afterPropertiesSet);
        assertThrows(IllegalStateException.class, enabled("@channel", null)::afterPropertiesSet);
        assertThrows(IllegalStateException.class, enabled("@channel", "invest.test")::afterPropertiesSet);

        final ChannelProperties window = enabled("@channel", "https://invest.test");
        window.setPostingFrom(window.getPostingTo());
        assertThrows(IllegalStateException.class, window::afterPropertiesSet);

        final ChannelProperties group = enabled("@channel", "https://invest.test");
        group.getWeeklyTop().setGroups(List.of(new ChannelProperties.Group().setTitle("X").setMin("Z").setMax("AAA")));
        assertThrows(IllegalStateException.class, group::afterPropertiesSet);
    }

    @Test
    void pageUrlWithoutDoubleSlash() {
        assertThat(enabled("@channel", "https://invest.test/").page("/bonds/X"), equalTo("https://invest.test/bonds/X"));
        assertThat(enabled("@channel", "https://invest.test").page("/bonds"), equalTo("https://invest.test/bonds"));
    }

    private static Optional<Change> classify(final NewsSource source, final String title) {
        return RatingActionClassifier.classify(source, title);
    }

    private static ChannelProperties enabled(final String chatId, final String siteUrl) {
        final ChannelProperties properties = new ChannelProperties();
        properties.setEnabled(true);
        properties.setChatId(chatId);
        properties.setSiteUrl(siteUrl);
        return properties;
    }
}
