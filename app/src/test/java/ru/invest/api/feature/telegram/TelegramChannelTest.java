package ru.invest.api.feature.telegram;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.telegram.bot.channel.ChannelProperties;
import ru.invest.api.telegram.bot.channel.ChannelUseCase;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Публичный Telegram-канал: сбор постов из ленты и подборки, публикация с окном, лимитом и паузой. Новости, эмитенты,
 * рейтинги и доходность вставляются SQL; сбор и публикация вызываются напрямую с заданным временем (шедулера и сбора
 * после загрузки ленты в тестах нет).
 */
@Sql(scripts = "/sql/bonds-for-controller.sql", statements = {
        "truncate table channel_post restart identity",
        "truncate table news restart identity cascade",
        "truncate table bond_issuer restart identity"})
public class TelegramChannelTest extends AbstractInvestApplicationTest {
    private static final String BANK = "RU000A10EQ34";
    private static final long BANK_EMITTER = 100L;
    private static final String CHANNEL = "@invest_test_channel";
    private static final int MESSAGE_ID = 42;
    // понедельник 16.11.2026, 08:00 МСК - до окна публикации (с 09:00)
    private static final Instant NOW = Instant.parse("2026-11-16T05:00:00Z");
    private static final Instant NINE_MSK = Instant.parse("2026-11-16T06:00:00Z");
    private static final String DOWNGRADE = "АКРА понизило кредитный рейтинг АО «Авто Финанс Банк» до BBB+(RU)";

    @Autowired
    private ChannelUseCase channelUseCase;
    @Autowired
    private ChannelProperties properties;
    @Autowired
    private TelegramClient telegramClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private List<CacheManager> cacheManagers;

    @BeforeEach
    @SneakyThrows
    void setUp() {
        final Message message = new Message();
        message.setMessageId(MESSAGE_ID);
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(message);
    }

    @AfterEach
    void restoreProperties() {
        properties.setEnabled(true);
        properties.setMaxPostsPerDay(5);
        properties.getWeeklyTop().setMinTurnoverRub(BigDecimal.valueOf(1_000_000));
    }

    // ---------- рейтинговые действия ----------

    @Test
    @SneakyThrows
    void ratingActionIsPostedOnceAndNotRepeatedAfterRestart() {
        issuer(BANK, BANK_EMITTER, "Акционерное общество \"Авто Финанс Банк\"");
        final long id = insertNews("ACRA", DOWNGRADE, null, BANK_EMITTER, NOW.minus(Duration.ofHours(1)));

        assertThat(channelUseCase.collectNews(NOW), equalTo(1));
        // повторная загрузка ленты и перезапуск: ключ поста уже есть
        assertThat(channelUseCase.collectNews(NOW), equalTo(0));
        assertThat(texts(), hasSize(1));
        assertThat(texts().getFirst(), equalTo("""
                Рейтинговое действие: понижение
                АКРА понизило кредитный рейтинг АО «Авто Финанс Банк» до BBB+(RU)
                Эмитент: АО "Авто Финанс Банк"

                АКРА, 16.11.2026
                Источник: https://news.test/acra-%d
                Облигации эмитента: https://invest.test/issuers/100""".formatted(id)));

        assertThat(channelUseCase.publish(NINE_MSK), equalTo(true));
        final ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getChatId(), equalTo(CHANNEL));
        assertThat(captor.getValue().getText(), equalTo(texts().getFirst()));
        assertThat(captor.getValue().getLinkPreviewOptions().getIsDisabled(), equalTo(true));
        assertThat(jdbcTemplate.queryForMap("select status, message_id from channel_post"),
                equalTo(Map.of("status", "SENT", "message_id", MESSAGE_ID)));

        // после публикации: новый сбор и новая публикация ничего не дают
        assertThat(channelUseCase.collectNews(NINE_MSK.plus(Duration.ofHours(1))), equalTo(0));
        assertThat(channelUseCase.publish(NINE_MSK.plus(Duration.ofHours(1))), equalTo(false));
        verify(telegramClient, times(1)).execute(any(SendMessage.class));
    }

    @Test
    void onlyRatingChangesAboutSystemBondsArePosted() {
        issuer(BANK, BANK_EMITTER, "Акционерное общество \"Авто Финанс Банк\"");
        final Instant hourAgo = NOW.minus(Duration.ofHours(1));
        // подтверждение и изменение прогноза - рейтинг не меняется
        insertNews("ACRA", "АКРА подтвердило кредитный рейтинг АО «Авто Финанс Банк» на уровне A(RU), прогноз «Стабильный»",
                null, BANK_EMITTER, hourAgo);
        insertNews("NKR", "НКР повысила прогноз по кредитному рейтингу ПАО «Авто Финанс Банк» A.ru до позитивного",
                null, BANK_EMITTER, hourAgo);
        // эмитент не из системы - нет страницы продукта
        insertNews("ACRA", "АКРА понизило кредитный рейтинг ООО «Чужой» до B(RU)", null, null, hourAgo);
        // старше max-age (2 дня)
        insertNews("ACRA", "АКРА повысило кредитный рейтинг АО «Авто Финанс Банк» до A+(RU)", null, BANK_EMITTER,
                NOW.minus(Duration.ofDays(3)));
        // рейтинг выпуска по ISIN, эмитент не привязан - страница облигации
        final long id = insertNews("NKR",
                "НКР присвоило выпуску облигаций ПАО «Авто Финанс Банк» серии БО-001Р-18 (RU000A10EQ34) кредитный рейтинг A.ru",
                BANK, null, hourAgo);

        assertThat(channelUseCase.collectNews(NOW), equalTo(1));
        assertThat(texts().getFirst(), equalTo("""
                Рейтинговое действие: присвоение
                НКР присвоило выпуску облигаций ПАО «Авто Финанс Банк» серии БО-001Р-18 (RU000A10EQ34) кредитный рейтинг A.ru
                Выпуски: Авто Финанс Банк БО-001Р-18 (RU000A10EQ34)

                НКР, 16.11.2026
                Источник: https://news.test/nkr-%d
                Облигации эмитента: https://invest.test/bonds/RU000A10EQ34""".formatted(id)));
    }

    // ---------- ключевая ставка ----------

    @Test
    void keyRateDecisionIsPosted() {
        final long id = insertNews("CBR_PRESS",
                "Банк России принял решение снизить ключевую ставку на 50 б.п., до 16,00% годовых", null, null,
                NOW.minus(Duration.ofHours(2)));
        insertNews("CBR_PRESS", "Банк России опубликовал данные о денежной массе", null, null, NOW.minus(Duration.ofHours(2)));

        assertThat(channelUseCase.collectNews(NOW), equalTo(1));
        assertThat(texts().getFirst(), equalTo("""
                Ключевая ставка
                Банк России принял решение снизить ключевую ставку на 50 б.п., до 16,00%% годовых

                Банк России, 16.11.2026
                Источник: https://news.test/cbr_press-%d
                Облигации с переменным купоном: https://invest.test/collections/floaters""".formatted(id)));
    }

    // ---------- флаг, окно, лимит, пауза ----------

    @Test
    void disabledChannelSendsNothing() {
        issuer(BANK, BANK_EMITTER, "Акционерное общество \"Авто Финанс Банк\"");
        insertNews("ACRA", DOWNGRADE, null, BANK_EMITTER, NOW.minus(Duration.ofHours(1)));
        assertThat(channelUseCase.collectNews(NOW), equalTo(1));

        properties.setEnabled(false);
        insertNews("NKR", "НКР понизила кредитный рейтинг ПАО «Авто Финанс Банк» с A+.ru до A.ru", null, BANK_EMITTER,
                NOW.minus(Duration.ofHours(1)));

        assertThat(channelUseCase.collectNews(NOW), equalTo(0));
        assertThat(channelUseCase.collectWeeklyTop(NOW), equalTo(false));
        // уже стоящий в очереди пост тоже не уходит
        assertThat(channelUseCase.publish(NINE_MSK), equalTo(false));
        verifyNoInteractions(telegramClient);
        assertThat(texts(), hasSize(1));
    }

    @Test
    @SneakyThrows
    void postingWindowDailyLimitAndPause() {
        issuer(BANK, BANK_EMITTER, "Акционерное общество \"Авто Финанс Банк\"");
        for (int i = 0; i < 3; i++) {
            insertNews("ACRA", DOWNGRADE + " " + i, null, BANK_EMITTER, NOW.minus(Duration.ofHours(3 - i)));
        }
        properties.setMaxPostsPerDay(2);
        assertThat(channelUseCase.collectNews(NOW), equalTo(3));

        // 08:59 МСК - до окна
        assertThat(channelUseCase.publish(NINE_MSK.minusSeconds(60)), equalTo(false));
        assertThat(channelUseCase.publish(NINE_MSK), equalTo(true));
        // через 10 минут - пауза 30 минут ещё не прошла
        assertThat(channelUseCase.publish(NINE_MSK.plus(Duration.ofMinutes(10))), equalTo(false));
        assertThat(channelUseCase.publish(NINE_MSK.plus(Duration.ofMinutes(30))), equalTo(true));
        // лимит дня - 2
        assertThat(channelUseCase.publish(NINE_MSK.plus(Duration.ofHours(2))), equalTo(false));
        // 21:00 МСК - окно закрыто
        assertThat(channelUseCase.publish(Instant.parse("2026-11-16T18:00:00Z")), equalTo(false));
        // на следующий день - третий, старые первыми
        assertThat(channelUseCase.publish(NINE_MSK.plus(Duration.ofDays(1))), equalTo(true));

        final ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, times(3)).execute(captor.capture());
        assertThat(captor.getAllValues().stream().map(message -> message.getText().lines().toList().get(1)).toList(),
                equalTo(List.of(DOWNGRADE + " 0", DOWNGRADE + " 1", DOWNGRADE + " 2")));
    }

    @Test
    void stalePostIsSkipped() {
        issuer(BANK, BANK_EMITTER, "Акционерное общество \"Авто Финанс Банк\"");
        insertNews("ACRA", DOWNGRADE, null, BANK_EMITTER, NOW.minus(Duration.ofHours(1)));
        assertThat(channelUseCase.collectNews(NOW), equalTo(1));

        // через 2 дня и 4 часа: событие старше max-age
        assertThat(channelUseCase.publish(NINE_MSK.plus(Duration.ofDays(2))), equalTo(false));
        verifyNoInteractions(telegramClient);
        assertThat(jdbcTemplate.queryForObject("select status from channel_post", String.class), equalTo("SKIPPED"));
    }

    @Test
    @SneakyThrows
    void networkErrorIsRetried() {
        issuer(BANK, BANK_EMITTER, "Акционерное общество \"Авто Финанс Банк\"");
        insertNews("ACRA", DOWNGRADE, null, BANK_EMITTER, NOW.minus(Duration.ofHours(1)));
        assertThat(channelUseCase.collectNews(NOW), equalTo(1));
        final Message message = new Message();
        message.setMessageId(MESSAGE_ID);
        when(telegramClient.execute(any(SendMessage.class)))
                .thenThrow(new TelegramApiException("timeout"))
                .thenReturn(message);

        assertThat(channelUseCase.publish(NINE_MSK), equalTo(false));
        assertThat(jdbcTemplate.queryForMap("select status, attempts, last_error from channel_post"),
                equalTo(Map.of("status", "PENDING", "attempts", 1, "last_error", "timeout")));
        // повтор - не раньше чем через 5 минут
        assertThat(channelUseCase.publish(NINE_MSK.plus(Duration.ofMinutes(1))), equalTo(false));
        assertThat(channelUseCase.publish(NINE_MSK.plus(Duration.ofMinutes(5))), equalTo(true));
        verify(telegramClient, times(2)).execute(any(SendMessage.class));
    }

    // ---------- еженедельная подборка ----------

    @Test
    @SneakyThrows
    void weeklyTopByRatingGroupsOncePerWeek() {
        // без свечей оборот не посчитать - фильтр оборота в этом тесте снят
        properties.getWeeklyTop().setMinTurnoverRub(null);
        assertThat(channelUseCase.collectWeeklyTop(NOW), equalTo(false));

        issuer(BANK, BANK_EMITTER, "Акционерное общество \"Авто Финанс Банк\"");
        jdbcTemplate.update("""
                insert into credit_rating (agency, scope, emitter_id, rating, grade, score, withdrawn, action_date)
                values ('NKR', 'ISSUER', ?, 'A.ru', 'A', 15, false, ?)""", BANK_EMITTER, LocalDateTime.now().toLocalDate());
        jdbcTemplate.update("update coupon set yield_to_maturity = ? where ticker = ?", new BigDecimal("20.5"), BANK);
        clearCaches();

        assertThat(channelUseCase.collectWeeklyTop(NOW), equalTo(true));
        // та же неделя - не повторяется
        assertThat(channelUseCase.collectWeeklyTop(NOW.plus(Duration.ofDays(2))), equalTo(false));
        final String text = texts().getFirst();
        assertThat(text, containsString("""
                Доходность облигаций по группам рейтинга на 16.11.2026
                Наибольшая доходность к погашению по последней цене: рублёвые выпуски с постоянным купоном, до погашения \
                от 0,5 г. Рейтинг - самый низкий из рейтингов АКРА и НКР.

                Рейтинг A+ – A-
                1. Авто Финанс Банк БО-001Р-18 (RU000A10EQ34): 20,50 %, погашение 14.03.2029, A
                https://invest.test/bonds/RU000A10EQ34

                Все облигации и фильтры: https://invest.test/bonds
                Не является индивидуальной инвестиционной рекомендацией."""));
        // пустые группы не показываются
        assertThat(text, not(containsString("AAA – AA-")));

        assertThat(channelUseCase.collectWeeklyTop(NOW.plus(Duration.ofDays(7))), equalTo(true));
        assertThat(texts(), hasSize(2));
        verify(telegramClient, never()).execute(any(SendMessage.class));
    }

    private List<String> texts() {
        return jdbcTemplate.queryForList("select text from channel_post order by id", String.class);
    }

    private void issuer(final String isin, final long emitterId, final String title) {
        jdbcTemplate.update("insert into bond_issuer (isin, emitter_id, title, updated_at) values (?, ?, ?, now())",
                isin, emitterId, title);
    }

    private long insertNews(final String source, final String title, final String isin, final Long emitterId,
                            final Instant publishedAt) {
        final Long id = jdbcTemplate.queryForObject("""
                insert into news (source, external_id, title, url, published_at, created_at)
                values (?, ?, ?, ?, ?, now()) returning id""", Long.class, source, title.hashCode() + "-" + System.nanoTime(),
                title, null, LocalDateTime.ofInstant(publishedAt, ZoneOffset.UTC));
        jdbcTemplate.update("update news set url = ? where id = ?",
                "https://news.test/" + source.toLowerCase() + "-" + id, id);
        if (isin != null) {
            jdbcTemplate.update("insert into news_link (news_id, isin) values (?, ?)", id, isin);
        }
        if (emitterId != null) {
            jdbcTemplate.update("insert into news_link (news_id, emitter_id) values (?, ?)", id, emitterId);
        }
        return id;
    }

    private void clearCaches() {
        cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames()
                .forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)));
    }
}
