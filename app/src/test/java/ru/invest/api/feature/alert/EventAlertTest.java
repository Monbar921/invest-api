package ru.invest.api.feature.alert;

import lombok.SneakyThrows;
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
import ru.invest.api.alert.service.usecase.AlertCheckUseCase;
import ru.invest.api.alert.service.usecase.NewsAlertCheckUseCase;
import ru.invest.api.dto.alert.AlertDto;
import ru.invest.api.dto.alert.AlertTypeDto;
import ru.invest.api.dto.alert.NotificationDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.telegram.bot.handler.TelegramUpdateHandler;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;

/**
 * Правила по событиям: новости облигации, рейтинги эмитента, ключевая ставка, объявленный купон.
 * Новости вставляются SQL после создания правила: правило видит только новости новее последней на момент создания.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql", statements = {
        "truncate table notification, bond_alert restart identity",
        "truncate table telegram_link",
        "truncate table news restart identity cascade"})
public class EventAlertTest extends AbstractInvestApplicationTest {
    private static final String BASE_URL = "http://localhost:8099/internal/rest/alerts";
    private static final String ALICE = "alice-sub";
    private static final String TICKER = "RU000A10EQ34";
    private static final int CREATED = 201;
    private static final int UNPROCESSABLE = 422;
    private static final String KEY_RATE_TITLE = "Банк России снизил ключевую ставку на 50 б.п., до 16,00% годовых";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private NewsAlertCheckUseCase newsAlertCheckUseCase;
    @Autowired
    private AlertCheckUseCase alertCheckUseCase;
    @Autowired
    private TelegramUpdateHandler updateHandler;
    @Autowired
    private TelegramClient telegramClient;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void onlyNewsAfterCreationAreSent() {
        insertNews("MOEX", "Старая новость о выпуске", TICKER);
        create(Map.of("ticker", TICKER, "type", "BOND_NEWS"));

        assertThat(newsAlertCheckUseCase.checkAll(), equalTo(0));

        insertNews("MOEX", "Изменение параметров выпуска RU000A10EQ34", TICKER);
        assertThat(newsAlertCheckUseCase.checkAll(), equalTo(1));
        assertThat(newsAlertCheckUseCase.checkAll(), equalTo(0));

        final String text = notifications().getFirst().getText();
        assertThat(text, containsString("Новость по облигации Авто Финанс Банк БО-001Р-18 (RU000A10EQ34)\n"
                + "Изменение параметров выпуска RU000A10EQ34\nМосбиржа, "));
        assertThat(text, containsString("https://news.test/"));
    }

    @Test
    void newsOfOtherBondsAreIgnored() {
        create(Map.of("ticker", TICKER, "type", "BOND_NEWS"));

        insertNews("MOEX", "Про другой выпуск", "RU000A1080Y2");

        assertThat(newsAlertCheckUseCase.checkAll(), equalTo(0));
    }

    @Test
    void ratingRuleSendsOnlyRatingAgencies() {
        create(Map.of("ticker", TICKER, "type", "ISSUER_RATING"));

        insertNews("MOEX", "Обычная новость биржи", TICKER);
        insertNews("ACRA", "АКРА понизило кредитный рейтинг АО «Авто Финанс Банк» до BBB+(RU)", TICKER);

        assertThat(newsAlertCheckUseCase.checkAll(), equalTo(1));
        assertThat(notifications().getFirst().getText(), containsString("Рейтинговое действие: Авто Финанс Банк"));
        assertThat(notifications().getFirst().getText(), containsString("\nАКРА, "));
        // биржевая новость уже просмотрена - не придёт и позже
        assertThat(newsAlertCheckUseCase.checkAll(), equalTo(0));
    }

    @Test
    void manyNewsAtOnceAreLimited() {
        create(Map.of("ticker", TICKER, "type", "BOND_NEWS"));
        for (int i = 1; i <= 8; i++) {
            insertNews("MOEX", "Новость " + i, TICKER);
        }

        assertThat(newsAlertCheckUseCase.checkAll(), equalTo(5));
        // последние пять, без первых трёх
        assertThat(notifications().stream().map(NotificationDto::getText).anyMatch(text -> text.contains("Новость 3\n")),
                equalTo(false));
        assertThat(notifications().stream().map(NotificationDto::getText).anyMatch(text -> text.contains("Новость 8\n")),
                equalTo(true));
    }

    @Test
    void keyRateDecisionWithoutBond() {
        final AlertDto alert = create(Map.of("type", "KEY_RATE"));
        assertThat(alert.getTicker(), nullValue());

        insertNews("CBR_PRESS", "Банк России опубликовал статистику платёжного баланса", null);
        insertNews("CBR_PRESS", KEY_RATE_TITLE, null);
        // как ЦБ формулирует сейчас
        insertNews("CBR_PRESS", "Банк России принял решение сохранить ключевую ставку на уровне 14,00% годовых", null);

        assertThat(newsAlertCheckUseCase.checkAll(), equalTo(2));
        assertThat(notifications().stream().map(NotificationDto::getText).anyMatch(text -> text.startsWith(
                "Ключевая ставка Банка России\n" + KEY_RATE_TITLE + "\nБанк России, ")), equalTo(true));
        assertThat(notifications().stream().map(NotificationDto::getText).anyMatch(text -> text.contains(
                "принял решение сохранить ключевую ставку")), equalTo(true));
        // одно такое правило на пользователя
        assertThat(post(Map.of("type", "KEY_RATE")), equalTo(UNPROCESSABLE));
    }

    @Test
    void reenabledNewsRuleSkipsMissedNews() {
        final AlertDto alert = create(Map.of("ticker", TICKER, "type", "BOND_NEWS"));
        update(alert.getId(), Map.of("enabled", false));
        insertNews("MOEX", "Пока правило выключено", TICKER);

        update(alert.getId(), Map.of("enabled", true));

        assertThat(newsAlertCheckUseCase.checkAll(), equalTo(0));
    }

    @Test
    void announcedCouponIsSentOnce() {
        // правило уже видело купоны по 14.12.2026
        jdbcTemplate.update("""
                insert into bond_alert (user_id, ticker, type, enabled, created_at, cursor_date)
                values (?, ?, 'FLOATER_COUPON', true, now(), date '2026-12-14')""", ALICE, TICKER);
        insertCoupon(LocalDate.of(2027, 1, 14), "0");

        // размер ещё не объявлен
        assertThat(alertCheckUseCase.checkAll(LocalDate.of(2026, 11, 12)), equalTo(0));

        jdbcTemplate.update("update coupon_data set price = 16.20 where payment_date = ?",
                LocalDate.of(2027, 1, 14).atStartOfDay());
        assertThat(alertCheckUseCase.checkAll(LocalDate.of(2026, 11, 12)), equalTo(1));
        assertThat(alertCheckUseCase.checkAll(LocalDate.of(2026, 11, 13)), equalTo(0));
        assertThat(notifications().getFirst().getText(), equalTo("""
                Объявлен купон 14.01.2027: 16,2 ₽ на облигацию (прошлый 15,71 ₽)
                Авто Финанс Банк БО-001Р-18 (RU000A10EQ34)"""));
    }

    @Test
    void eventRulesValidation() {
        assertThat(post(Map.of("type", "BOND_NEWS")), equalTo(UNPROCESSABLE));
        create(Map.of("ticker", TICKER, "type", "BOND_NEWS"));
        assertThat(post(Map.of("ticker", TICKER, "type", "BOND_NEWS")), equalTo(UNPROCESSABLE));
    }

    @Test
    @SneakyThrows
    void listShowsEventRules() {
        jdbcTemplate.update("insert into telegram_link (user_id, chat_id, linked_at) values (?, 4001, now())", ALICE);
        create(Map.of("type", "KEY_RATE"));
        create(Map.of("ticker", TICKER, "type", "ISSUER_RATING"));

        final Message message = new Message();
        message.setChat(new Chat(4001L, "private"));
        message.setFrom(User.builder().id(4001L).firstName("Test").isBot(false).build());
        message.setText("/list");
        final Update update = new Update();
        update.setMessage(message);
        updateHandler.consume(update);

        final ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText(), containsString("• Банк России - решения по ключевой ставке"));
        assertThat(captor.getValue().getText(), containsString("(RU000A10EQ34) - рейтинги эмитента"));
    }

    @Test
    void notificationsHistoryIsEmptyWithoutNews() {
        create(Map.of("ticker", TICKER, "type", "BOND_NEWS"));

        assertThat(newsAlertCheckUseCase.checkAll(), equalTo(0));
        assertThat(notifications(), hasSize(0));
    }

    private void insertNews(final String source, final String title, final String isin) {
        final Long id = jdbcTemplate.queryForObject("""
                insert into news (source, external_id, title, url, published_at, created_at)
                values (?, ?, ?, ?, ?, now()) returning id""", Long.class, source, title.hashCode() + "-" + System.nanoTime(),
                title, "https://news.test/" + Math.abs(title.hashCode()), LocalDateTime.of(2026, 11, 12, 10, 0));
        if (isin != null) {
            jdbcTemplate.update("insert into news_link (news_id, isin) values (?, ?)", id, isin);
        }
    }

    private void insertCoupon(final LocalDate date, final String price) {
        jdbcTemplate.update("""
                insert into coupon_data (id, coupon_id, ticker, uid, price, currency, fix_date, payment_date, created_at,
                                         created_by)
                select (select max(id) + 1 from coupon_data), 4, ticker, uid, ?, 'rub', ?, ?, now(), 'TEST'
                from bond where ticker = ?""", new BigDecimal(price), date.minusDays(1).atStartOfDay(),
                date.atStartOfDay(), TICKER);
    }

    private AlertDto create(final Map<String, Object> body) {
        final HttpResponse<String> response = send("POST", "", body);
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        return jsonMapper.readValue(response.body(), AlertDto.class);
    }

    private int post(final Map<String, Object> body) {
        return send("POST", "", body).statusCode();
    }

    private void update(final long id, final Map<String, Object> body) {
        send("PUT", "/" + id, new HashMap<>(body));
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
