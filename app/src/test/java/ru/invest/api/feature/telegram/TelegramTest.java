package ru.invest.api.feature.telegram;

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
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.dto.telegram.TelegramLinkDto;
import ru.invest.api.dto.telegram.TelegramStatusDto;
import ru.invest.api.telegram.bot.handler.TelegramUpdateHandler;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Sql(statements = {"truncate table telegram_link", "truncate table telegram_link_token"})
public class TelegramTest extends AbstractInvestApplicationTest {
    private static final String BASE_URL = "http://localhost:8099/internal/rest/alerts/telegram";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ALICE = "alice-sub";
    private static final String BOB = "bob-sub";
    private static final long CHAT_1 = 1001L;
    private static final long CHAT_2 = 1002L;
    private static final int OK = 200;
    private static final int NO_CONTENT = 204;
    private static final int UNAUTHORIZED = 401;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private TelegramClient telegramClient;
    @Autowired
    private TelegramUpdateHandler updateHandler;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void requestsWithoutUserAreRejected() {
        assertThat(send("GET", "", null).statusCode(), equalTo(UNAUTHORIZED));
        assertThat(send("POST", "/link-token", null).statusCode(), equalTo(UNAUTHORIZED));
        assertThat(send("DELETE", "", " ").statusCode(), equalTo(UNAUTHORIZED));
    }

    @Test
    void statusOfNewUser() {
        final TelegramStatusDto status = status(ALICE);

        assertThat(status.isEnabled(), equalTo(true));
        assertThat(status.getBotUsername(), equalTo("invest_test_bot"));
        assertThat(status.isLinked(), equalTo(false));
        assertThat(status.getUsername(), nullValue());
        assertThat(status.getLinkedAt(), nullValue());
    }

    @Test
    void linkTokenIsOneTimeUrl() {
        final TelegramLinkDto link = createLink(ALICE);

        assertThat(link.getUrl(), startsWith("https://t.me/invest_test_bot?start="));
        // start-параметр Telegram: до 64 символов A-Z a-z 0-9 _ -
        assertThat(token(link), matchesPattern("[A-Za-z0-9_-]{43}"));
        assertThat(link.getExpiresAt().isAfter(ZonedDateTime.now().plusMinutes(9)), equalTo(true));
        assertThat(link.getExpiresAt().isBefore(ZonedDateTime.now().plusMinutes(11)), equalTo(true));
        // в БД - только хеш токена
        assertThat(jdbcTemplate.queryForObject("select count(*) from telegram_link_token where token_hash = ?",
                Integer.class, token(link)), equalTo(0));
    }

    @Test
    void startWithTokenLinksChat() {
        final String token = token(createLink(ALICE));

        message(CHAT_1, "/start " + token);

        assertThat(lastReply(CHAT_1), containsString("Telegram подключён"));
        final TelegramStatusDto status = status(ALICE);
        assertThat(status.isLinked(), equalTo(true));
        assertThat(status.getUsername(), equalTo("user" + CHAT_1));
        assertThat(status.getLinkedAt(), notNullValue());
        assertThat(status.getAvailable(), equalTo(true));
    }

    @Test
    void tokenWorksOnlyOnce() {
        final String token = token(createLink(ALICE));
        message(CHAT_1, "/start " + token);

        message(CHAT_2, "/start " + token);

        assertThat(lastReply(CHAT_2), containsString("Ссылка недействительна"));
        assertThat(chatOf(ALICE), equalTo(List.of(CHAT_1)));
    }

    @Test
    void newLinkInvalidatesPreviousToken() {
        final String first = token(createLink(ALICE));
        final String second = token(createLink(ALICE));

        message(CHAT_1, "/start " + first);
        assertThat(lastReply(CHAT_1), containsString("Ссылка недействительна"));

        message(CHAT_1, "/start " + second);
        assertThat(lastReply(CHAT_1), containsString("Telegram подключён"));
    }

    @Test
    void expiredTokenIsRejected() {
        final String token = token(createLink(ALICE));
        jdbcTemplate.update("update telegram_link_token set expires_at = timezone('UTC', now()) - interval '1 minute'");
        // expires_at - UTC без зоны, а now() сессии - в зоне JVM

        message(CHAT_1, "/start " + token);

        assertThat(lastReply(CHAT_1), containsString("Ссылка недействительна"));
        assertThat(status(ALICE).isLinked(), equalTo(false));
        assertThat(jdbcTemplate.queryForObject("select count(*) from telegram_link_token", Integer.class), equalTo(0));
    }

    @Test
    void unknownTokenIsRejected() {
        message(CHAT_1, "/start not-a-real-token");

        assertThat(lastReply(CHAT_1), containsString("Ссылка недействительна"));
    }

    @Test
    void chatMovesToAnotherUser() {
        message(CHAT_1, "/start " + token(createLink(ALICE)));

        message(CHAT_1, "/start " + token(createLink(BOB)));

        assertThat(status(ALICE).isLinked(), equalTo(false));
        assertThat(chatOf(BOB), equalTo(List.of(CHAT_1)));
    }

    @Test
    void userRelinksToAnotherChat() {
        message(CHAT_1, "/start " + token(createLink(ALICE)));

        message(CHAT_2, "/start " + token(createLink(ALICE)));

        assertThat(chatOf(ALICE), equalTo(List.of(CHAT_2)));
        message(CHAT_1, "/stop");
        assertThat(lastReply(CHAT_1), containsString("не подключён"));
    }

    @Test
    void stopUnlinksChat() {
        message(CHAT_1, "/start " + token(createLink(ALICE)));

        message(CHAT_1, "/stop");

        assertThat(lastReply(CHAT_1), containsString("Telegram отключён"));
        assertThat(status(ALICE).isLinked(), equalTo(false));
    }

    @Test
    void deleteUnlinksChat() {
        message(CHAT_1, "/start " + token(createLink(ALICE)));

        assertThat(send("DELETE", "", ALICE).statusCode(), equalTo(NO_CONTENT));
        assertThat(status(ALICE).isLinked(), equalTo(false));
        // повторно - тоже 204
        assertThat(send("DELETE", "", ALICE).statusCode(), equalTo(NO_CONTENT));
    }

    @Test
    void startWithoutTokenAndOtherTextGetHints() {
        message(CHAT_1, "/start");
        assertThat(lastReply(CHAT_1), containsString("нажмите «Подключить Telegram»"));

        message(CHAT_1, "привет");
        assertThat(lastReply(CHAT_1), containsString("/stop"));

        message(CHAT_1, "/START@invest_test_bot");
        assertThat(lastReply(CHAT_1), containsString("нажмите «Подключить Telegram»"));
    }

    @Test
    @SneakyThrows
    void groupChatsAreIgnored() {
        final String token = token(createLink(ALICE));
        final Update update = update(CHAT_1, "/start " + token);
        update.getMessage().setChat(new Chat(CHAT_1, "group"));

        updateHandler.consume(update);

        verify(telegramClient, never()).execute(any(SendMessage.class));
        assertThat(status(ALICE).isLinked(), equalTo(false));
    }

    @Test
    @SneakyThrows
    void failedSendMarksTelegramUnavailable() {
        when(telegramClient.execute(any(SendMessage.class))).thenThrow(new TelegramApiRequestException("timeout"));

        message(CHAT_1, "/start " + token(createLink(ALICE)));

        final TelegramStatusDto status = status(ALICE);
        // привязка сохранена, хотя ответ в чат не ушёл
        assertThat(status.isLinked(), equalTo(true));
        assertThat(status.getAvailable(), equalTo(false));
        assertThat(status.getCheckedAt(), notNullValue());
    }

    private TelegramStatusDto status(final String userId) {
        final HttpResponse<String> response = send("GET", "", userId);
        assertThat(response.statusCode(), equalTo(OK));
        return read(response.body(), TelegramStatusDto.class);
    }

    private TelegramLinkDto createLink(final String userId) {
        final HttpResponse<String> response = send("POST", "/link-token", userId);
        assertThat(response.statusCode(), equalTo(OK));
        return read(response.body(), TelegramLinkDto.class);
    }

    private static String token(final TelegramLinkDto link) {
        return link.getUrl().substring(link.getUrl().indexOf("start=") + "start=".length());
    }

    private List<Long> chatOf(final String userId) {
        return jdbcTemplate.queryForList("select chat_id from telegram_link where user_id = ?", Long.class, userId);
    }

    private void message(final long chatId, final String text) {
        updateHandler.consume(update(chatId, text));
    }

    private static Update update(final long chatId, final String text) {
        final Message message = new Message();
        message.setChat(new Chat(chatId, "private"));
        message.setFrom(User.builder().id(chatId).firstName("Test").isBot(false).userName("user" + chatId).build());
        message.setText(text);
        final Update update = new Update();
        update.setMessage(message);
        return update;
    }

    @SneakyThrows
    private String lastReply(final long chatId) {
        final ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient, atLeastOnce()).execute(captor.capture());
        final List<SendMessage> replies = captor.getAllValues()
                .stream()
                .filter(reply -> reply.getChatId().equals(String.valueOf(chatId)))
                .toList();
        assertThat(replies, not(hasSize(0)));
        return replies.getLast().getText();
    }

    @SneakyThrows
    private HttpResponse<String> send(final String method, final String path, final String userId) {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (userId != null) {
            request.header(USER_ID_HEADER, userId);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private <T> T read(final String json, final Class<T> type) {
        return jsonMapper.readValue(json, type);
    }
}
