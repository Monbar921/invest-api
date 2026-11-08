package ru.invest.api.feature.alert;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.alert.service.usecase.PriceAlertCheckUseCase;
import ru.invest.api.dto.alert.AlertDirectionDto;
import ru.invest.api.dto.alert.AlertDto;
import ru.invest.api.dto.alert.AlertStateDto;
import ru.invest.api.dto.alert.AlertTypeDto;
import ru.invest.api.dto.alert.NotificationDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.telegram.bot.handler.TelegramUpdateHandler;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;

/**
 * Правила по цене и доходности. Облигация RU000A10EQ34 из bonds-for-controller.sql: номинал 1000 ₽, цена 1005 ₽
 * (100,5% от номинала). Цена меняется SQL, кэш облигаций сбрасывается - как после синхронизации цен.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql", statements = {
        "truncate table notification, bond_alert restart identity",
        "truncate table telegram_link",
        "truncate table price_candle restart identity"})
public class PriceAlertTest extends AbstractInvestApplicationTest {
    private static final String BASE_URL = "http://localhost:8099/internal/rest/alerts";
    private static final String ALICE = "alice-sub";
    private static final String TICKER = "RU000A10EQ34";
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final int CREATED = 201;
    private static final int UNPROCESSABLE = 422;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private PriceAlertCheckUseCase priceAlertCheckUseCase;
    @Autowired
    private TinkoffBondCacheRepositoryUseCase bondCache;
    @Autowired
    private TelegramUpdateHandler updateHandler;
    @Autowired
    private TelegramClient telegramClient;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void freshCache() {
        bondCache.evictAll();
    }

    @Test
    void priceLevelFiresOnCrossingAndRearmsWithMargin() {
        final long id = insertRule(AlertTypeDto.PRICE_LEVEL, AlertDirectionDto.DOWN, "95", "ARMED", null);

        assertThat(checkAtPrice("1005"), equalTo(0));
        assertThat(checkAtPrice("940"), equalTo(1));
        assertThat(notifications().getFirst().getText(), equalTo("""
                Цена опустилась до 95% от номинала: сейчас 94%
                Авто Финанс Банк БО-001Р-18 (RU000A10EQ34)
                Цена: 940 ₽"""));
        assertThat(state(id), equalTo("TRIGGERED"));

        // ниже порога снова и колебание у порога (95% < 95 + 0,2) - без повторов
        assertThat(checkAtPrice("930"), equalTo(0));
        assertThat(checkAtPrice("950"), equalTo(0));
        assertThat(state(id), equalTo("TRIGGERED"));
        // вернулась за порог с запасом - взведено, новое пересечение - новое уведомление
        assertThat(checkAtPrice("960"), equalTo(0));
        assertThat(state(id), equalTo("ARMED"));
        assertThat(checkAtPrice("940"), equalTo(1));
        assertThat(notifications(), hasSize(2));
    }

    @Test
    void priceLevelUp() {
        insertRule(AlertTypeDto.PRICE_LEVEL, AlertDirectionDto.UP, "101", "ARMED", null);

        assertThat(checkAtPrice("1005"), equalTo(0));
        assertThat(checkAtPrice("1012.5"), equalTo(1));
        assertThat(notifications().getFirst().getText(), containsString("Цена выросла до 101% от номинала: сейчас 101,25%"));
    }

    @Test
    void yieldToMaturityLevel() {
        insertRule(AlertTypeDto.YTM_LEVEL, AlertDirectionDto.UP, "20", "ARMED", null);
        jdbcTemplate.update("update coupon set yield_to_maturity = 21.3 where ticker = ?", TICKER);
        bondCache.evictAll();

        assertThat(priceAlertCheckUseCase.checkAll(TODAY), equalTo(1));
        assertThat(notifications().getFirst().getText(), equalTo("""
                Доходность к погашению выросла до 20%: сейчас 21,3% годовых
                Авто Финанс Банк БО-001Р-18 (RU000A10EQ34)
                Цена: 100,5% от номинала, 1005 ₽"""));
    }

    @Test
    void unknownYieldIsSkipped() {
        // у флоатера или без расчёта доходности нет - правило ждёт
        insertRule(AlertTypeDto.YTO_LEVEL, AlertDirectionDto.UP, "10", "ARMED", null);

        assertThat(priceAlertCheckUseCase.checkAll(TODAY), equalTo(0));
    }

    @Test
    void priceChangeFromCreationFiresOnceAndDisables() {
        final AlertDto alert = create(Map.of("ticker", TICKER, "type", "PRICE_CHANGE", "direction", "ANY",
                "threshold", 5));
        assertThat(alert.getBaseValue(), comparesEqualTo(new BigDecimal("100.5")));
        assertThat(notifications(), hasSize(0));

        assertThat(checkAtPrice("960"), equalTo(0));
        assertThat(checkAtPrice("950"), equalTo(1));
        assertThat(notifications().getFirst().getText(), containsString("Цена изменилась на −5,47% с "));
        assertThat(notifications().getFirst().getText(), containsString("сейчас 95% от номинала (было 100,5%)"));
        assertThat(alerts().getFirst().isEnabled(), equalTo(false));
        assertThat(checkAtPrice("900"), equalTo(0));
    }

    @Test
    void reenabledPriceChangeCountsFromCurrentPrice() {
        final AlertDto alert = create(Map.of("ticker", TICKER, "type", "PRICE_CHANGE", "direction", "DOWN",
                "threshold", 5));
        checkAtPrice("950");
        setPrice("950");

        final AlertDto reenabled = update(alert.getId(), Map.of("enabled", true));

        assertThat(reenabled.getBaseValue(), comparesEqualTo(new BigDecimal("95")));
        assertThat(checkAtPrice("940"), equalTo(0));
        assertThat(checkAtPrice("900"), equalTo(1));
        assertThat(notifications(), hasSize(2));
    }

    @Test
    void dailyChangeAgainstPreviousCloseOncePerDay() {
        insertRule(AlertTypeDto.PRICE_DAILY_CHANGE, AlertDirectionDto.UP, "2", null, null);
        insertCandle(TODAY.minusDays(2), "96");
        insertCandle(TODAY.minusDays(1), "100");

        assertThat(checkAtPrice("1015"), equalTo(0));
        assertThat(checkAtPrice("1030"), equalTo(1));
        assertThat(notifications().getFirst().getText(), containsString(
                "Цена за день +3%: сейчас 103% от номинала (закрытие прошлого дня 100%)"));
        assertThat(checkAtPrice("1040"), equalTo(0));
        // следующий день - снова от закрытия прошлого торгового дня
        assertThat(priceAlertCheckUseCase.checkAll(TODAY.plusDays(1)), equalTo(1));
    }

    @Test
    void dailyChangeWithoutCandlesIsSkipped() {
        insertRule(AlertTypeDto.PRICE_DAILY_CHANGE, AlertDirectionDto.ANY, "1", null, null);

        assertThat(checkAtPrice("900"), equalTo(0));
    }

    @Test
    void levelAlreadyReachedOnCreateFiresAtOnce() {
        final AlertDto alert = create(Map.of("ticker", TICKER, "type", "PRICE_LEVEL", "direction", "UP",
                "threshold", 100));

        assertThat(notifications(), hasSize(1));
        assertThat(alerts().getFirst().getState(), equalTo(AlertStateDto.TRIGGERED));
        assertThat(alert.getDirection(), equalTo(AlertDirectionDto.UP));
    }

    @Test
    void newThresholdRearms() {
        final AlertDto alert = create(Map.of("ticker", TICKER, "type", "PRICE_LEVEL", "direction", "UP",
                "threshold", 100));

        update(alert.getId(), Map.of("threshold", 102));

        assertThat(alerts().getFirst().getState(), equalTo(AlertStateDto.ARMED));
        assertThat(checkAtPrice("1025"), equalTo(1));
        assertThat(notifications(), hasSize(2));
    }

    @Test
    void severalPriceRulesPerBondAreAllowed() {
        create(Map.of("ticker", TICKER, "type", "PRICE_LEVEL", "direction", "UP", "threshold", 110));
        create(Map.of("ticker", TICKER, "type", "PRICE_LEVEL", "direction", "DOWN", "threshold", 90));

        assertThat(alerts(), hasSize(2));
    }

    @Test
    void invalidPriceRulesAreRejected() {
        assertThat(post(Map.of("ticker", TICKER, "type", "PRICE_LEVEL", "direction", "ANY", "threshold", 95)),
                equalTo(UNPROCESSABLE));
        assertThat(post(Map.of("ticker", TICKER, "type", "PRICE_LEVEL", "direction", "DOWN", "threshold", 0)),
                equalTo(UNPROCESSABLE));
        assertThat(post(Map.of("ticker", TICKER, "type", "PRICE_LEVEL", "direction", "DOWN")),
                equalTo(UNPROCESSABLE));
        assertThat(post(Map.of("ticker", TICKER, "type", "PRICE_CHANGE", "direction", "UP", "threshold", 60)),
                equalTo(UNPROCESSABLE));
        assertThat(post(Map.of("ticker", TICKER, "type", "YTM_LEVEL", "direction", "UP", "threshold", 0)),
                equalTo(UNPROCESSABLE));
        assertThat(post(Map.of("ticker", TICKER, "type", "COUPON_BEFORE")), equalTo(UNPROCESSABLE));
    }

    @Test
    @SneakyThrows
    void listShowsPriceRules() {
        jdbcTemplate.update("insert into telegram_link (user_id, chat_id, linked_at) values (?, 3001, now())", ALICE);
        create(Map.of("ticker", TICKER, "type", "PRICE_LEVEL", "direction", "DOWN", "threshold", 95));
        create(Map.of("ticker", TICKER, "type", "PRICE_DAILY_CHANGE", "direction", "ANY", "threshold", 2.5));
        create(Map.of("ticker", TICKER, "type", "YTM_LEVEL", "direction", "UP", "threshold", 20));

        final Message message = new Message();
        message.setChat(new Chat(3001L, "private"));
        message.setFrom(User.builder().id(3001L).firstName("Test").isBot(false).build());
        message.setText("/list");
        final Update update = new Update();
        update.setMessage(message);
        updateHandler.consume(update);

        final ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        final String text = captor.getValue().getText();
        assertThat(text, containsString("цена ≤ 95% от номинала"));
        assertThat(text, containsString("цена за день ±2,5%"));
        assertThat(text, containsString("доходность к погашению ≥ 20%"));
    }

    private int checkAtPrice(final String price) {
        setPrice(price);
        return priceAlertCheckUseCase.checkAll(TODAY);
    }

    private void setPrice(final String price) {
        jdbcTemplate.update("update price set price = ? where ticker = ?", new BigDecimal(price), TICKER);
        bondCache.evictAll();
    }

    private long insertRule(final AlertTypeDto type, final AlertDirectionDto direction, final String threshold,
                            final String state, final String base) {
        return jdbcTemplate.queryForObject("""
                insert into bond_alert (user_id, ticker, type, direction, threshold, state, base_value, enabled,
                                        created_at)
                values (?, ?, ?, ?, ?, ?, ?, true, now()) returning id""", Long.class, ALICE, TICKER, type.name(),
                direction.name(), new BigDecimal(threshold), state, base == null ? null : new BigDecimal(base));
    }

    private void insertCandle(final LocalDate date, final String close) {
        jdbcTemplate.update("""
                insert into price_candle (bond_id, uid, candle_date, open, high, low, close, volume, created_at)
                select id, uid, ?, ?, ?, ?, ?, 100, now() from bond where ticker = ?""",
                date, new BigDecimal(close), new BigDecimal(close), new BigDecimal(close), new BigDecimal(close),
                TICKER);
    }

    private String state(final long id) {
        return jdbcTemplate.queryForObject("select state from bond_alert where id = ?", String.class, id);
    }

    private AlertDto create(final Map<String, Object> body) {
        final HttpResponse<String> response = send("POST", "", body);
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        return jsonMapper.readValue(response.body(), AlertDto.class);
    }

    private int post(final Map<String, Object> body) {
        return send("POST", "", body).statusCode();
    }

    private AlertDto update(final long id, final Map<String, Object> body) {
        return jsonMapper.readValue(send("PUT", "/" + id, new HashMap<>(body)).body(), AlertDto.class);
    }

    private List<AlertDto> alerts() {
        return jsonMapper.readValue(send("GET", "", null).body(), new TypeReference<>() {
        });
    }

    private List<NotificationDto> notifications() {
        final PageDto<NotificationDto> page = jsonMapper.readValue(send("GET", "/notifications", null).body(),
                new TypeReference<>() {
                });
        return page.getContent();
    }

    @SneakyThrows
    private HttpResponse<String> send(final String method, final String path, final Object body) {
        return httpClient.send(HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .header("X-User-Id", ALICE)
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
