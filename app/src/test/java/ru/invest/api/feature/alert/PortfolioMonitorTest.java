package ru.invest.api.feature.alert;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.chat.Chat;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.alert.service.usecase.PortfolioDigestUseCase;
import ru.invest.api.alert.service.usecase.PortfolioRiskCheckUseCase;
import ru.invest.api.dto.alert.AlertDto;
import ru.invest.api.dto.alert.AlertTypeDto;
import ru.invest.api.telegram.bot.handler.TelegramUpdateHandler;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;

/**
 * Монитор риска и дайджест портфеля. Новости, эмитенты, покупки и цены вставляются SQL; проверка монитора и дайджест
 * вызываются напрямую (после загрузки ленты в тестах проверка выключена, шедулера дайджеста в тестах нет).
 */
@Sql(scripts = "/sql/bonds-for-controller.sql", statements = {
        "truncate table notification, bond_alert restart identity",
        "truncate table telegram_link",
        "truncate table news restart identity cascade",
        "truncate table portfolio_sale, portfolio_position, portfolio_broker, bond_issuer, currency_rate "
                + "restart identity"})
public class PortfolioMonitorTest extends AbstractInvestApplicationTest {
    private static final String BASE_URL = "http://localhost:8099/internal/rest/alerts";
    private static final String ALICE = "alice-sub";
    private static final String BOB = "bob-sub";
    // bonds-for-controller.sql: Авто Финанс Банк, 1005 ₽ при номинале 1000 ₽; второй выпуск того же эмитента (в тесте)
    private static final String BANK = "RU000A10EQ34";
    private static final String BANK_OTHER = "RU000A1080Y2";
    // эмитент, которого нет в портфеле
    private static final String STRANGER = "RU000A10EHC1";
    private static final long BANK_EMITTER = 100L;
    private static final int CREATED = 201;
    private static final int UNPROCESSABLE = 422;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    // календарь выплат начинается не раньше настоящего «сегодня» - дайджест проверяется на нём и днях после
    private final LocalDate today = LocalDate.now(MOSCOW);

    @Autowired
    private PortfolioRiskCheckUseCase riskCheckUseCase;
    @Autowired
    private PortfolioDigestUseCase digestUseCase;
    @Autowired
    private TelegramUpdateHandler updateHandler;
    @Autowired
    private TelegramClient telegramClient;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private List<CacheManager> cacheManagers;

    @BeforeEach
    void setUp() {
        clearCaches();
        issuer(BANK, BANK_EMITTER, "Акционерное общество \"Авто Финанс Банк\"");
        issuer(BANK_OTHER, BANK_EMITTER, "Акционерное общество \"Авто Финанс Банк\"");
        issuer(STRANGER, 200L, "Общество с ограниченной ответственностью \"Гидромашсервис\"");
    }

    // ---------- монитор риска ----------

    @Test
    void riskRuleIsOnePerUserWithoutBond() {
        final AlertDto alert = create(Map.of("type", "PORTFOLIO_RISK"));

        assertThat(alert.getType(), equalTo(AlertTypeDto.PORTFOLIO_RISK));
        assertThat(alert.getTicker(), nullValue());
        assertThat(post(Map.of("type", "PORTFOLIO_RISK")), equalTo(UNPROCESSABLE));
    }

    @Test
    void oneEventIsOneNotificationForSeveralIssuesOfEmitter() {
        position(ALICE, BANK, 10);
        position(ALICE, BANK_OTHER, 5);
        create(Map.of("type", "PORTFOLIO_RISK"));

        insertNews("ACRA", "АКРА понизило кредитный рейтинг АО «Авто Финанс Банк» до BBB+(RU)", null, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 12, 0, 0));

        assertThat(riskCheckUseCase.checkAll(), equalTo(1));
        assertThat(riskCheckUseCase.checkAll(), equalTo(0));
        assertThat(texts(), hasSize(1));
        assertThat(texts().getFirst(), equalTo("""
                Риск по портфелю: понижение кредитного рейтинга
                Эмитент: АО "Авто Финанс Банк"
                В портфеле: Пушкинское ПЗ 001Р-03 (RU000A1080Y2), Авто Финанс Банк БО-001Р-18 (RU000A10EQ34)
                АКРА понизило кредитный рейтинг АО «Авто Финанс Банк» до BBB+(RU)
                АКРА, 12.11.2026
                https://news.test/acra-1"""));
    }

    @Test
    void sameEventFromOtherSourcesIsNotRepeated() {
        position(ALICE, BANK, 10);
        create(Map.of("type", "PORTFOLIO_RISK"));

        insertNews("INTERFAX", "«Авто Финанс Банк» допустил дефолт по купону", BANK, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 12, 9, 0));
        insertNews("RBC", "Авто Финанс Банк объявил дефолт", null, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 12, 11, 0));
        assertThat(riskCheckUseCase.checkAll(), equalTo(1));

        // на следующий день пишут ещё - в пределах трёх дней это то же событие
        insertNews("KOMMERSANT", "Дефолт «Авто Финанс Банка»: что делать держателям", null, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 13, 9, 0));
        assertThat(riskCheckUseCase.checkAll(), equalTo(0));

        // другой вид риска по тому же эмитенту - новое событие
        insertNews("INTERFAX", "«Авто Финанс Банк» допустил технический дефолт", null, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 13, 10, 0));
        assertThat(riskCheckUseCase.checkAll(), equalTo(1));

        // через неделю - снова
        insertNews("RBC", "Авто Финанс Банк снова допустил дефолт", null, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 20, 9, 0));
        assertThat(riskCheckUseCase.checkAll(), equalTo(1));
        assertThat(texts(), hasSize(3));
        assertThat(texts().getFirst(), startsWith("Риск по портфелю: дефолт\n"));
    }

    @Test
    void tradingSuspensionOfOtherIssueIsIgnored() {
        position(ALICE, BANK, 10);
        create(Map.of("type", "PORTFOLIO_RISK"));

        // приостановлен другой выпуск того же эмитента - торги облигацией пользователя идут
        insertNews("MOEX", "О приостановке торгов ценными бумагами", BANK_OTHER, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 12, 10, 0));
        assertThat(riskCheckUseCase.checkAll(), equalTo(0));

        insertNews("MOEX", "О приостановке торгов ценными бумагами", BANK, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 12, 12, 0));
        assertThat(riskCheckUseCase.checkAll(), equalTo(1));
        assertThat(texts().getFirst(), containsString("Риск по портфелю: приостановка торгов\n"));
        assertThat(texts().getFirst(), containsString("В портфеле: Авто Финанс Банк БО-001Р-18 (RU000A10EQ34)\n"));
    }

    @Test
    void onlyNewRiskNewsOfOwnPortfolio() {
        position(ALICE, BANK, 10);
        position(BOB, STRANGER, 3);
        insertNews("ACRA", "АКРА понизило кредитный рейтинг АО «Авто Финанс Банк» до A-(RU)", null, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 11, 0, 0));
        create(Map.of("type", "PORTFOLIO_RISK"));

        // старая новость - до включения монитора
        assertThat(riskCheckUseCase.checkAll(), equalTo(0));
        // не риск
        insertNews("MOEX", "Изменение параметров выпуска RU000A10EQ34", BANK, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 12, 10, 0));
        // риск эмитента, которого у Алисы нет (он у Боба, но у Боба нет монитора)
        insertNews("INTERFAX", "«Гидромашсервис» допустил дефолт", STRANGER, 200L,
                LocalDateTime.of(2026, 11, 12, 10, 0));
        assertThat(riskCheckUseCase.checkAll(), equalTo(0));
        assertThat(texts(), hasSize(0));
    }

    @Test
    void disabledMonitorSkipsMissedNews() {
        position(ALICE, BANK, 10);
        final AlertDto alert = create(Map.of("type", "PORTFOLIO_RISK"));
        update(alert.getId(), Map.of("enabled", false));

        insertNews("INTERFAX", "«Авто Финанс Банк» допустил дефолт", BANK, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 12, 10, 0));
        assertThat(riskCheckUseCase.checkAll(), equalTo(0));

        update(alert.getId(), Map.of("enabled", true));
        assertThat(riskCheckUseCase.checkAll(), equalTo(0));
    }

    // ---------- дайджест ----------

    @Test
    void digestValidation() {
        assertThat(post(Map.of("type", "PORTFOLIO_DIGEST")), equalTo(UNPROCESSABLE));
        assertThat(post(Map.of("type", "PORTFOLIO_DIGEST", "days", 3)), equalTo(UNPROCESSABLE));
        final AlertDto alert = create(Map.of("type", "PORTFOLIO_DIGEST", "days", 7));
        assertThat(alert.getDays(), equalTo(7));
        assertThat(post(Map.of("type", "PORTFOLIO_DIGEST", "days", 1)), equalTo(UNPROCESSABLE));
        // период меняется: только 1 или 7
        assertThat(send("PUT", "/" + alert.getId(), Map.of("days", 2)).statusCode(), equalTo(UNPROCESSABLE));
        update(alert.getId(), Map.of("days", 1));
        assertThat(send("GET", "", null).body(), containsString("\"days\":1"));
    }

    @Test
    void emptyPortfolioDigestIsNotSent() {
        final AlertDto alert = create(Map.of("type", "PORTFOLIO_DIGEST", "days", 1));

        assertThat(digestUseCase.send(alert.getId(), today), equalTo(false));
        assertThat(texts(), hasSize(0));
    }

    @Test
    void dailyDigestValueChangeAndEmptyDays() {
        position(ALICE, BANK, 10);
        coupon(today, "15.21");
        final AlertDto alert = create(Map.of("type", "PORTFOLIO_DIGEST", "days", 1));

        // первый: стоимость без изменения и выплата сегодня
        assertThat(digestUseCase.send(alert.getId(), today), equalTo(true));
        assertThat(texts().getFirst(), equalTo("""
                Дайджест портфеля за день, %s
                Стоимость: 10 050,00 ₽

                Выплаты сегодня:
                • %s купон Авто Финанс Банк БО-001Р-18 (RU000A10EQ34), 10 шт.: 152,1 ₽
                Итого: 152,10 ₽""".formatted(today.format(DATE), today.format(DATE))));
        // тот же день - не повторяется
        assertThat(digestUseCase.send(alert.getId(), today), equalTo(false));

        // ничего не изменилось, выплат и новостей нет - пустой дайджест не отправляется
        assertThat(digestUseCase.send(alert.getId(), today.plusDays(1)), equalTo(false));

        // цена выросла: изменение - от прошлого отправленного дайджеста
        jdbcTemplate.update("update price set price = 1015 where ticker = ?", BANK);
        clearCaches();
        assertThat(digestUseCase.send(alert.getId(), today.plusDays(2)), equalTo(true));
        assertThat(texts().getFirst(), equalTo("""
                Дайджест портфеля за день, %s
                Стоимость: 10 150,00 ₽ (+100,00 ₽, +1%% с %s)""".formatted(today.plusDays(2).format(DATE),
                LocalDate.now(MOSCOW).format(DATE))));

        // докупили - изменение было бы не от цены, оно не считается
        position(ALICE, BANK, 1);
        clearCaches();
        assertThat(digestUseCase.send(alert.getId(), today.plusDays(3)), equalTo(true));
        assertThat(texts().getFirst(), containsString("Стоимость: 11 165,00 ₽ (состав портфеля менялся с "));
        assertThat(texts().getFirst(), containsString(" - изменение не считается)"));
    }

    @Test
    void digestListsNewsOfPortfolioBonds() {
        position(ALICE, BANK, 10);
        insertNews("MOEX", "Старая новость о выпуске", BANK, BANK_EMITTER, LocalDateTime.of(2026, 11, 10, 10, 0));
        final AlertDto alert = create(Map.of("type", "PORTFOLIO_DIGEST", "days", 1));
        assertThat(digestUseCase.send(alert.getId(), today), equalTo(true));
        assertThat(texts().getFirst(), not(containsString("Новости")));

        insertNews("MOEX", "Изменение параметров выпуска RU000A10EQ34", BANK, null,
                LocalDateTime.of(2026, 11, 12, 10, 0));
        // новость эмитента, а не выпуска - тоже про облигацию портфеля
        insertNews("ACRA", "АКРА подтвердило кредитный рейтинг АО «Авто Финанс Банк»", null, BANK_EMITTER,
                LocalDateTime.of(2026, 11, 12, 0, 0));
        insertNews("MOEX", "Про чужой выпуск", STRANGER, 200L, LocalDateTime.of(2026, 11, 12, 10, 0));

        assertThat(digestUseCase.send(alert.getId(), today.plusDays(1)), equalTo(true));
        assertThat(texts().getFirst(), equalTo("""
                Дайджест портфеля за день, %s
                Стоимость: 10 050,00 ₽ (без изменений с %s)

                Новости по облигациям портфеля:
                • АКРА подтвердило кредитный рейтинг АО «Авто Финанс Банк» (АКРА, 12.11.2026)
                • Изменение параметров выпуска RU000A10EQ34 (Мосбиржа, 12.11.2026)""".formatted(
                today.plusDays(1).format(DATE), LocalDate.now(MOSCOW).format(DATE))));
        // показанные новости в следующий дайджест не попадают - он пустой
        assertThat(digestUseCase.send(alert.getId(), today.plusDays(2)), equalTo(false));
    }

    @Test
    void weeklyDigestOnlyOnItsDay() {
        position(ALICE, BANK, 10);
        create(Map.of("type", "PORTFOLIO_DIGEST", "days", 7));
        final LocalDate monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        assertThat(digestUseCase.sendDue(monday.plusDays(1)), equalTo(0));
        assertThat(digestUseCase.sendDue(monday), equalTo(1));
        assertThat(texts().getFirst(), startsWith("Дайджест портфеля за неделю, " + monday.format(DATE)));
        assertThat(digestUseCase.sendDue(monday), equalTo(0));
    }

    @Test
    @SneakyThrows
    void listShowsPortfolioRules() {
        jdbcTemplate.update("insert into telegram_link (user_id, chat_id, linked_at) values (?, 4002, now())", ALICE);
        create(Map.of("type", "PORTFOLIO_RISK"));
        create(Map.of("type", "PORTFOLIO_DIGEST", "days", 7));

        final Message message = new Message();
        message.setChat(new Chat(4002L, "private"));
        message.setFrom(User.builder().id(4002L).firstName("Test").isBot(false).build());
        message.setText("/list");
        final Update update = new Update();
        update.setMessage(message);
        updateHandler.consume(update);

        final ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramClient).execute(captor.capture());
        assertThat(captor.getValue().getText(),
                containsString("• Портфель - риски: рейтинг, приостановка торгов, дефолт"));
        assertThat(captor.getValue().getText(), containsString("• Портфель - дайджест раз в неделю"));
    }

    // ---------- помощники ----------

    /** Тексты уведомлений, новые первыми; неразрывные пробелы разрядов - обычные */
    private List<String> texts() {
        return jdbcTemplate.queryForList("select text from notification order by id desc", String.class).stream()
                .map(text -> text.replace(' ', ' ').replace(' ', ' '))
                .toList();
    }

    private void position(final String userId, final String ticker, final int quantity) {
        jdbcTemplate.update("insert into portfolio_position (user_id, ticker, quantity, buy_price, buy_date, created_at)"
                + " values (?, ?, ?, 100, ?, now())", userId, ticker, quantity, LocalDate.now(ZoneOffset.UTC).minusDays(30));
    }

    private void issuer(final String isin, final long emitterId, final String title) {
        jdbcTemplate.update("insert into bond_issuer (isin, emitter_id, title, updated_at) values (?, ?, ?, now())",
                isin, emitterId, title);
    }

    private void coupon(final LocalDate date, final String price) {
        jdbcTemplate.update("""
                insert into coupon_data (id, coupon_id, ticker, uid, price, currency, fix_date, payment_date, created_at,
                                         created_by)
                select (select max(id) + 1 from coupon_data), 4, ticker, uid, ?, 'rub', ?, ?, now(), 'TEST'
                from bond where ticker = ?""", new BigDecimal(price), date.minusDays(1).atStartOfDay(),
                date.atStartOfDay(), BANK);
    }

    private void insertNews(final String source, final String title, final String isin, final Long emitterId,
                            final LocalDateTime publishedAt) {
        final Long id = jdbcTemplate.queryForObject("""
                insert into news (source, external_id, title, url, published_at, created_at)
                values (?, ?, ?, ?, ?, now()) returning id""", Long.class, source, title.hashCode() + "-" + System.nanoTime(),
                title, null, publishedAt);
        jdbcTemplate.update("update news set url = ? where id = ?",
                "https://news.test/" + source.toLowerCase() + "-" + id, id);
        if (isin != null) {
            jdbcTemplate.update("insert into news_link (news_id, isin) values (?, ?)", id, isin);
        }
        if (emitterId != null) {
            jdbcTemplate.update("insert into news_link (news_id, emitter_id) values (?, ?)", id, emitterId);
        }
    }

    private void clearCaches() {
        cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames()
                .forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)));
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
