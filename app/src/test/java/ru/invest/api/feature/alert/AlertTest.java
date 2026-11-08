package ru.invest.api.feature.alert;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.alert.service.usecase.AlertCheckUseCase;
import ru.invest.api.alert.service.usecase.NotificationDispatchUseCase;
import ru.invest.api.dto.alert.AlertDto;
import ru.invest.api.dto.alert.AlertTypeDto;
import ru.invest.api.dto.alert.NotificationDto;
import ru.invest.api.dto.alert.NotificationStatusDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.telegram.bot.handler.TelegramUpdateHandler;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Sql(scripts = "/sql/bonds-for-controller.sql", statements = {
        "truncate table notification, bond_alert restart identity",
        "truncate table telegram_link"})
public class AlertTest extends AbstractInvestApplicationTest {
    private static final String BASE_URL = "http://localhost:8099/internal/rest/alerts";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ALICE = "alice-sub";
    private static final String BOB = "bob-sub";
    private static final long ALICE_CHAT = 2001L;
    // купоны 14.11.2026 (15,21) и 14.12.2026 (15,71), погашение 14.03.2029 - из bonds-for-controller.sql
    private static final String AUTO_FINANCE = "RU000A10EQ34";
    private static final LocalDate BEFORE_NOVEMBER_COUPON = LocalDate.of(2026, 11, 12);
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int NO_CONTENT = 204;
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int NOT_FOUND = 404;
    private static final int UNPROCESSABLE = 422;
    private static final int FORBIDDEN = 403;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private AlertCheckUseCase alertCheckUseCase;
    @Autowired
    private NotificationDispatchUseCase dispatchUseCase;
    @Autowired
    private TelegramUpdateHandler updateHandler;
    @Autowired
    private TelegramClient telegramClient;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void requestsWithoutUserAreRejected() {
        assertThat(send("GET", "", null, null).statusCode(), equalTo(UNAUTHORIZED));
        assertThat(send("POST", "", null, Map.of("ticker", AUTO_FINANCE, "type", "COUPON_BEFORE", "days", 3))
                .statusCode(), equalTo(UNAUTHORIZED));
        assertThat(send("GET", "/notifications", null, null).statusCode(), equalTo(UNAUTHORIZED));
    }

    @Test
    void createAndListAlerts() {
        final AlertDto created = create(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);

        assertThat(created.getId(), notNullValue());
        assertThat(created.getBondName(), equalTo("Авто Финанс Банк БО-001Р-18"));
        assertThat(created.getIsin(), equalTo(AUTO_FINANCE));
        assertThat(created.getDays(), equalTo(3));
        assertThat(created.isEnabled(), equalTo(true));
        create(ALICE, AUTO_FINANCE, AlertTypeDto.MATURITY_BEFORE, 30);

        assertThat(alerts(ALICE, AUTO_FINANCE).stream().map(AlertDto::getType).toList(),
                contains(AlertTypeDto.COUPON_BEFORE, AlertTypeDto.MATURITY_BEFORE));
        assertThat(alerts(ALICE, null), hasSize(2));
        assertThat(alerts(BOB, null), empty());
    }

    @Test
    void invalidAlertsAreRejected() {
        assertThat(post(ALICE, Map.of("ticker", AUTO_FINANCE, "type", "COUPON_BEFORE", "days", 0)).statusCode(),
                equalTo(BAD_REQUEST));
        assertThat(post(ALICE, Map.of("ticker", AUTO_FINANCE, "type", "COUPON_BEFORE", "days", 61)).statusCode(),
                equalTo(BAD_REQUEST));
        assertThat(post(ALICE, Map.of("ticker", AUTO_FINANCE, "type", "PRICE_SOMETHING", "days", 3)).statusCode(),
                equalTo(BAD_REQUEST));
        assertThat(post(ALICE, Map.of("ticker", "NO_SUCH_BOND", "type", "COUPON_BEFORE", "days", 3)).statusCode(),
                equalTo(NOT_FOUND));

        create(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);
        assertThat(post(ALICE, Map.of("ticker", AUTO_FINANCE, "type", "COUPON_BEFORE", "days", 5)).statusCode(),
                equalTo(UNPROCESSABLE));
    }

    @Test
    void updateAndDeleteOnlyOwnAlerts() {
        final AlertDto alert = create(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);

        assertThat(send("PUT", "/" + alert.getId(), BOB, Map.of("days", 5)).statusCode(), equalTo(NOT_FOUND));
        assertThat(send("DELETE", "/" + alert.getId(), BOB, null).statusCode(), equalTo(NOT_FOUND));

        final HttpResponse<String> updated = send("PUT", "/" + alert.getId(), ALICE, Map.of("days", 7, "enabled", false));
        assertThat(updated.statusCode(), equalTo(OK));
        assertThat(read(updated.body(), AlertDto.class).getDays(), equalTo(7));
        assertThat(read(updated.body(), AlertDto.class).isEnabled(), equalTo(false));

        assertThat(send("DELETE", "/" + alert.getId(), ALICE, null).statusCode(), equalTo(NO_CONTENT));
        assertThat(alerts(ALICE, null), empty());
    }

    @Test
    void couponAlertIsQueuedOncePerCoupon() {
        insertAlert(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);

        assertThat(alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON), equalTo(1));
        // повторная проверка в тот же и на следующий день - тот же купон, без дублей
        assertThat(alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON), equalTo(0));
        assertThat(alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON.plusDays(1)), equalTo(0));

        final List<NotificationDto> history = notifications(ALICE);
        assertThat(history, hasSize(1));
        assertThat(history.getFirst().getStatus(), equalTo(NotificationStatusDto.PENDING));
        assertThat(history.getFirst().getText(), equalTo("""
                Купон через 2 дня, 14.11.2026
                Авто Финанс Банк БО-001Р-18 (RU000A10EQ34)
                Выплата: 15,21 ₽ на облигацию."""));
        assertThat(alerts(ALICE, null).getFirst().getLastTriggeredAt(), notNullValue());
    }

    @Test
    void eventOutsideWindowIsNotQueued() {
        insertAlert(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 1);

        // до купона 2 дня, а предупредить просили за 1
        assertThat(alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON), equalTo(0));
        assertThat(alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON.plusDays(1)), equalTo(1));
        assertThat(notifications(ALICE).getFirst().getText(), containsString("Купон завтра, 14.11.2026"));
    }

    @Test
    void wideWindowQueuesEveryEventInside() {
        insertAlert(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 40);

        // в 40 днях - и ноябрьский, и декабрьский купон
        assertThat(alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON), equalTo(2));
    }

    @Test
    void maturityAlertHasNominal() {
        insertAlert(ALICE, AUTO_FINANCE, AlertTypeDto.MATURITY_BEFORE, 60);

        assertThat(alertCheckUseCase.checkAll(LocalDate.of(2029, 2, 1)), equalTo(1));
        assertThat(notifications(ALICE).getFirst().getText(), equalTo("""
                Погашение через 41 день, 14.03.2029
                Авто Финанс Банк БО-001Р-18 (RU000A10EQ34)
                Выплата: 1000 ₽ на облигацию."""));
    }

    @Test
    void disabledAlertIsNotChecked() {
        final AlertDto alert = create(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);
        send("PUT", "/" + alert.getId(), ALICE, Map.of("enabled", false));

        assertThat(alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON), equalTo(0));
    }

    @Test
    void eventAlreadyInWindowIsQueuedOnCreate() {
        // оферта через 2 дня от настоящего «сегодня»: правило на 3 дня срабатывает сразу, без утренней проверки
        final LocalDate offer = LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(2);
        jdbcTemplate.update("update bond set offer_date = ? where ticker = ?", offer.atStartOfDay(), AUTO_FINANCE);

        create(ALICE, AUTO_FINANCE, AlertTypeDto.OFFER_BEFORE, 3);

        final List<NotificationDto> history = notifications(ALICE);
        assertThat(history, hasSize(1));
        assertThat(history.getFirst().getText(), containsString("Оферта через 2 дня"));
        assertThat(history.getFirst().getText(), containsString("предъявить к выкупу"));
    }

    @Test
    void goneBondAlertsAreRemovedWithNotice() {
        jdbcTemplate.update("""
                insert into bond_alert (user_id, ticker, type, days, enabled, created_at)
                values (?, 'GONE_BOND', 'COUPON_BEFORE', 3, true, now())""", ALICE);

        assertThat(alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON), equalTo(1));

        assertThat(alerts(ALICE, null), empty());
        final NotificationDto notice = notifications(ALICE).getFirst();
        assertThat(notice.getText(), containsString("GONE_BOND больше нет"));
        assertThat(notice.getAlertId(), nullValue());
    }

    @Test
    @SneakyThrows
    void dispatchSendsWithDisableButton() {
        linkTelegram(ALICE, ALICE_CHAT);
        final long alertId = insertAlert(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);
        alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON);

        assertThat(dispatchUseCase.dispatch(), equalTo(1));

        final ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        final SendMessage message = captor.getValue();
        assertThat(message.getChatId(), equalTo(String.valueOf(ALICE_CHAT)));
        assertThat(message.getText(), containsString("Купон через 2 дня"));
        final InlineKeyboardMarkup keyboard = (InlineKeyboardMarkup) message.getReplyMarkup();
        assertThat(keyboard.getKeyboard().getFirst().getFirst().getCallbackData(),
                equalTo("alert-off:" + alertId));

        final NotificationDto sent = notifications(ALICE).getFirst();
        assertThat(sent.getStatus(), equalTo(NotificationStatusDto.SENT));
        assertThat(sent.getSentAt(), notNullValue());
        // отправленное второй раз не уходит
        assertThat(dispatchUseCase.dispatch(), equalTo(0));
    }

    @Test
    @SneakyThrows
    void withoutTelegramNotificationFails() {
        insertAlert(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);
        alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON);

        assertThat(dispatchUseCase.dispatch(), equalTo(0));

        final NotificationDto failed = notifications(ALICE).getFirst();
        assertThat(failed.getStatus(), equalTo(NotificationStatusDto.FAILED));
        assertThat(failed.getLastError(), equalTo("Telegram не подключён"));
        verify(telegramClient, never()).execute(any(SendMessage.class));
    }

    @Test
    @SneakyThrows
    void networkErrorIsRetriedLater() {
        linkTelegram(ALICE, ALICE_CHAT);
        insertAlert(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);
        alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON);
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("timeout"));

        assertThat(dispatchUseCase.dispatch(), equalTo(0));

        final NotificationDto pending = notifications(ALICE).getFirst();
        assertThat(pending.getStatus(), equalTo(NotificationStatusDto.PENDING));
        assertThat(pending.getAttempts(), equalTo(1));
        assertThat(pending.getLastError(), equalTo("timeout"));
        // следующая попытка - через минуту: сейчас разбор её не берёт
        assertThat(dispatchUseCase.dispatch(), equalTo(0));
        assertThat(notifications(ALICE).getFirst().getAttempts(), equalTo(1));

        // время пришло - уходит
        jdbcTemplate.update("update notification set next_attempt_at = ?", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        when(telegramClient.execute(any(SendMessage.class))).thenReturn(null);
        assertThat(dispatchUseCase.dispatch(), equalTo(1));
    }

    @Test
    @SneakyThrows
    void retriesStopAfterADay() {
        linkTelegram(ALICE, ALICE_CHAT);
        insertAlert(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);
        alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON);
        jdbcTemplate.update("update notification set created_at = ?", LocalDateTime.now(ZoneOffset.UTC).minusHours(25));
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiException("timeout"));

        dispatchUseCase.dispatch();

        final NotificationDto failed = notifications(ALICE).getFirst();
        assertThat(failed.getStatus(), equalTo(NotificationStatusDto.FAILED));
        assertThat(failed.getLastError(), containsString("Не отправлено за 24 ч"));
    }

    @Test
    @SneakyThrows
    void blockedBotUnlinksTelegram() {
        linkTelegram(ALICE, ALICE_CHAT);
        insertAlert(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);
        alertCheckUseCase.checkAll(BEFORE_NOVEMBER_COUPON);
        final TelegramApiRequestException blocked = org.mockito.Mockito.mock(TelegramApiRequestException.class);
        when(blocked.getErrorCode()).thenReturn(FORBIDDEN);
        when(blocked.getMessage()).thenReturn("Forbidden: bot was blocked by the user");
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(blocked);

        dispatchUseCase.dispatch();

        assertThat(notifications(ALICE).getFirst().getStatus(), equalTo(NotificationStatusDto.FAILED));
        assertThat(jdbcTemplate.queryForObject("select count(*) from telegram_link", Integer.class), equalTo(0));
    }

    @Test
    @SneakyThrows
    void disableButtonTurnsAlertOff() {
        linkTelegram(ALICE, ALICE_CHAT);
        final AlertDto alert = create(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);

        updateHandler.consume(callback(ALICE_CHAT, "alert-off:" + alert.getId()));

        assertThat(alerts(ALICE, null).getFirst().isEnabled(), equalTo(false));
        final ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText(), equalTo("Уведомление отключено"));
    }

    @Test
    @SneakyThrows
    void disableButtonIgnoresOthersAlerts() {
        linkTelegram(ALICE, ALICE_CHAT);
        final AlertDto bobs = create(BOB, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);

        updateHandler.consume(callback(ALICE_CHAT, "alert-off:" + bobs.getId()));

        assertThat(alerts(BOB, null).getFirst().isEnabled(), equalTo(true));
        final ArgumentCaptor<AnswerCallbackQuery> captor = ArgumentCaptor.forClass(AnswerCallbackQuery.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText(), equalTo("Уведомление уже удалено"));
    }

    @Test
    @SneakyThrows
    void listCommandShowsAlerts() {
        linkTelegram(ALICE, ALICE_CHAT);
        final AlertDto coupon = create(ALICE, AUTO_FINANCE, AlertTypeDto.COUPON_BEFORE, 3);
        create(ALICE, AUTO_FINANCE, AlertTypeDto.OFFER_BEFORE, 14);
        send("PUT", "/" + coupon.getId(), ALICE, Map.of("enabled", false));

        updateHandler.consume(message(ALICE_CHAT, "/list"));

        final ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText(), equalTo("""
                Ваши уведомления:
                • Авто Финанс Банк БО-001Р-18 (RU000A10EQ34) - купон за 3 дн. (выключено)
                • Авто Финанс Банк БО-001Р-18 (RU000A10EQ34) - оферта за 14 дн.

                Изменить - колокольчиком у облигации в приложении."""));
    }

    private AlertDto create(final String userId, final String ticker, final AlertTypeDto type, final int days) {
        final HttpResponse<String> response = post(userId, Map.of("ticker", ticker, "type", type.name(), "days", days));
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        return read(response.body(), AlertDto.class);
    }

    /**
     * Правило мимо REST: создание через API сразу проверяет правило по настоящей дате, а эти тесты проверяют
     * разбор на заданную дату.
     */
    private long insertAlert(final String userId, final String ticker, final AlertTypeDto type, final int days) {
        return jdbcTemplate.queryForObject("""
                insert into bond_alert (user_id, ticker, type, days, enabled, created_at)
                values (?, ?, ?, ?, true, now()) returning id""", Long.class, userId, ticker, type.name(), days);
    }

    private HttpResponse<String> post(final String userId, final Map<String, Object> body) {
        return send("POST", "", userId, body);
    }

    private List<AlertDto> alerts(final String userId, final String ticker) {
        final HttpResponse<String> response = send("GET", ticker == null ? "" : "?ticker=" + ticker, userId, null);
        assertThat(response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    private List<NotificationDto> notifications(final String userId) {
        final HttpResponse<String> response = send("GET", "/notifications", userId, null);
        assertThat(response.statusCode(), equalTo(OK));
        final PageDto<NotificationDto> page = jsonMapper.readValue(response.body(), new TypeReference<>() {
        });
        return page.getContent();
    }

    private void linkTelegram(final String userId, final long chatId) {
        jdbcTemplate.update("insert into telegram_link (user_id, chat_id, linked_at) values (?, ?, now())",
                userId, chatId);
    }

    private static Update message(final long chatId, final String text) {
        final Message message = new Message();
        message.setChat(new Chat(chatId, "private"));
        message.setFrom(user(chatId));
        message.setText(text);
        final Update update = new Update();
        update.setMessage(message);
        return update;
    }

    private static Update callback(final long chatId, final String data) {
        final CallbackQuery callback = new CallbackQuery();
        callback.setId("cb-" + chatId);
        callback.setFrom(user(chatId));
        callback.setChatInstance("instance");
        callback.setData(data);
        final Update update = new Update();
        update.setCallbackQuery(callback);
        return update;
    }

    private static User user(final long id) {
        return User.builder().id(id).firstName("Test").isBot(false).userName("user" + id).build();
    }

    @SneakyThrows
    private HttpResponse<String> send(final String method, final String path, final String userId,
                                      final Object body) {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)));
        if (userId != null) {
            request.header(USER_ID_HEADER, userId);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private <T> T read(final String json, final Class<T> type) {
        return jsonMapper.readValue(json, type);
    }
}
