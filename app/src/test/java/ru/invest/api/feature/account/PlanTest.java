package ru.invest.api.feature.account;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.dto.account.AccountPlanDto;
import ru.invest.api.dto.account.PlanDto;
import ru.invest.api.dto.account.PlanFeatureDto;
import ru.invest.api.dto.account.PlanLimitDto;
import ru.invest.api.dto.account.PlanLimitUsageDto;
import ru.invest.api.dto.portfolio.CalendarLinkDto;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Тарифы и права: тариф по умолчанию, выдача и отзыв вручную, истечение срока, лимиты (402) и закрытая фича.
 * Тарифы - из app/src/test/resources/application.yml: free (50 правил, 200 покупок, календарь), basic (40 правил, без
 * календаря), premium (52 правила, 300 покупок, календарь). Правила и покупки сверх лимита вставляются SQL.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql", statements = "truncate table account_auto_renewal, payment,"
        + " account_subscription, notification, bond_alert, portfolio_sale, portfolio_position, portfolio_broker,"
        + " portfolio_calendar_token restart identity")
public class PlanTest extends AbstractInvestApplicationTest {
    private static final String BASE_URL = "http://localhost:8099/internal/rest";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ALICE = "plan-alice";
    private static final String BOB = "plan-bob";
    // bonds-for-controller.sql
    private static final String OFZ = "SU26238RMFS4";
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int NO_CONTENT = 204;
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int PAYMENT_REQUIRED = 402;
    private static final int UNPROCESSABLE = 422;
    private static final int FREE_ALERTS = 50;
    private static final int PREMIUM_ALERTS = 52;
    private static final int FREE_POSITIONS = 200;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        // подписки других тестов не касаются: у остальных пользователей тариф по умолчанию
        jdbcTemplate.update("truncate table account_auto_renewal, payment, account_subscription restart identity");
    }

    @Test
    void requestsWithoutUserAreRejected() {
        assertThat(send("GET", "/account/plan", null, null).statusCode(), equalTo(UNAUTHORIZED));
    }

    @Test
    void newUserHasDefaultPlan() {
        jdbcTemplate.update("insert into bond_alert (user_id, ticker, type, days, enabled, created_at)"
                + " values (?, 'T1', 'COUPON_BEFORE', 3, true, now())", ALICE);

        final AccountPlanDto plan = plan(ALICE);

        assertThat(plan.getPlan().getId(), equalTo("free"));
        assertThat(plan.isDefaultPlan(), equalTo(true));
        assertThat(plan.getSource(), nullValue());
        assertThat(plan.getValidUntil(), nullValue());
        assertThat(plan.getPlan().getFeatures(), contains(PlanFeatureDto.CALENDAR_ICS));
        assertThat(plan.getLimits().stream().map(PlanLimitUsageDto::getLimit).toList(),
                contains(PlanLimitDto.ALERTS, PlanLimitDto.PORTFOLIO_POSITIONS));
        assertThat(usage(plan, PlanLimitDto.ALERTS).getMax(), equalTo(FREE_ALERTS));
        assertThat(usage(plan, PlanLimitDto.ALERTS).getUsed(), equalTo(1L));
        assertThat(usage(plan, PlanLimitDto.PORTFOLIO_POSITIONS).getMax(), equalTo(FREE_POSITIONS));
        assertThat(usage(plan, PlanLimitDto.PORTFOLIO_POSITIONS).getUsed(), equalTo(0L));
        // все тарифы в порядке настройки
        assertThat(plan.getPlans().stream().map(PlanDto::getId).toList(), contains("free", "basic", "premium"));
        assertThat(plan.getPlans().get(1).getFeatures(), empty());
        assertThat(plan.getPlans().get(2).isPaid(), equalTo(true));
        assertThat(plan.getPlans().get(2).getLimits().get(PlanLimitDto.ALERTS), equalTo(PREMIUM_ALERTS));
    }

    @Test
    void grantAndRevokeChangeLimitsImmediately() {
        alerts(ALICE, FREE_ALERTS);

        // лимит бесплатного исчерпан: 402 с лимитом и подсказкой о тарифе, где больше
        final HttpResponse<String> rejected = createAlert(ALICE, "COUPON_BEFORE");
        assertThat(rejected.body(), rejected.statusCode(), equalTo(PAYMENT_REQUIRED));
        final JsonNode problem = jsonMapper.readTree(rejected.body());
        assertThat(problem.get("limit").asString(), equalTo("ALERTS"));
        assertThat(problem.get("max").asInt(), equalTo(FREE_ALERTS));
        assertThat(problem.get("plan").asString(), equalTo("free"));
        assertThat(problem.get("detail").asString(), containsString("не больше 50 правил уведомлений"));
        assertThat(problem.get("detail").asString(), containsString("«Премиум» - до 52"));

        final LocalDate until = today().plusDays(30);
        final AccountPlanDto granted = grant(ALICE, "premium", until);
        assertThat(granted.getPlan().getId(), equalTo("premium"));
        assertThat(granted.isDefaultPlan(), equalTo(false));
        assertThat(granted.getSource(), equalTo("MANUAL"));
        assertThat(granted.getValidUntil(), equalTo(until));
        assertThat(usage(granted, PlanLimitDto.ALERTS).getMax(), equalTo(PREMIUM_ALERTS));

        // сразу действует новый лимит
        assertThat(createAlert(ALICE, "COUPON_BEFORE").statusCode(), equalTo(CREATED));
        // кто выдал - из заголовка From, срок - до конца последнего дня по Москве
        assertThat(jdbcTemplate.queryForObject("select created_by from account_subscription where user_id = ?",
                String.class, ALICE), equalTo("tester"));

        assertThat(maintenance("DELETE", ALICE, null).statusCode(), equalTo(NO_CONTENT));
        final AccountPlanDto revoked = plan(ALICE);
        assertThat(revoked.getPlan().getId(), equalTo("free"));
        assertThat(revoked.isDefaultPlan(), equalTo(true));
        // правила сверх лимита не удаляются, но новые не создаются
        assertThat(usage(revoked, PlanLimitDto.ALERTS).getUsed(), equalTo(FREE_ALERTS + 1L));
        assertThat(createAlert(ALICE, "MATURITY_BEFORE").statusCode(), equalTo(PAYMENT_REQUIRED));
        // повторный отзыв - тоже 204; история остаётся
        assertThat(maintenance("DELETE", ALICE, null).statusCode(), equalTo(NO_CONTENT));
        assertThat(jdbcTemplate.queryForObject("select count(*) from account_subscription where user_id = ?"
                + " and revoked_at is not null", Long.class, ALICE), equalTo(1L));

        // у другого пользователя - свой тариф
        assertThat(plan(BOB).isDefaultPlan(), equalTo(true));
    }

    @Test
    void expiredSubscriptionReturnsToDefaultPlan() {
        jdbcTemplate.update("insert into portfolio_position (user_id, ticker, quantity, buy_price, buy_date, created_at)"
                + " select ?, 'P' || n, 1, 100, current_date, now() from generate_series(1, ?) n", ALICE, FREE_POSITIONS);
        subscription(ALICE, "premium", "now() - interval '10 days'", "now() - interval '1 minute'");

        final AccountPlanDto plan = plan(ALICE);
        assertThat(plan.getPlan().getId(), equalTo("free"));
        assertThat(plan.isDefaultPlan(), equalTo(true));
        final HttpResponse<String> rejected = createPosition(ALICE);
        assertThat(rejected.body(), rejected.statusCode(), equalTo(PAYMENT_REQUIRED));
        assertThat(jsonMapper.readTree(rejected.body()).get("limit").asString(), equalTo("PORTFOLIO_POSITIONS"));

        // подписка ещё не началась - тоже не действует; действующая - действует
        subscription(ALICE, "premium", "now() + interval '1 day'", null);
        assertThat(plan(ALICE).getPlan().getId(), equalTo("free"));
        subscription(ALICE, "premium", "now() - interval '1 day'", "now() + interval '1 day'");
        assertThat(plan(ALICE).getPlan().getId(), equalTo("premium"));
        assertThat(createPosition(ALICE).statusCode(), equalTo(CREATED));
    }

    @Test
    void featureOutsidePlanIsClosed() {
        // бесплатный включает подписку на календарь
        final CalendarLinkDto link = calendarLink(ALICE);

        grant(ALICE, "basic", null);
        final HttpResponse<String> rejected = send("POST", "/portfolio/calendar/link", ALICE, null);
        assertThat(rejected.body(), rejected.statusCode(), equalTo(PAYMENT_REQUIRED));
        final JsonNode problem = jsonMapper.readTree(rejected.body());
        assertThat(problem.get("feature").asString(), equalTo("CALENDAR_ICS"));
        assertThat(problem.get("plan").asString(), equalTo("basic"));
        assertThat(problem.get("detail").asString(), containsString("не входит в тариф «Базовый»"));
        // ссылка, созданная раньше, перестаёт отдавать календарь, но не удаляется
        assertThat(send("GET", "/calendar/" + link.getToken() + ".ics", null, null).statusCode(),
                equalTo(PAYMENT_REQUIRED));
        // бессрочная подписка
        assertThat(plan(ALICE).getValidUntil(), nullValue());

        assertThat(maintenance("DELETE", ALICE, null).statusCode(), equalTo(NO_CONTENT));
        assertThat(send("GET", "/calendar/" + link.getToken() + ".ics", null, null).statusCode(), equalTo(OK));
    }

    @Test
    void newGrantReplacesActiveSubscription() {
        grant(ALICE, "basic", null);
        final AccountPlanDto plan = grant(ALICE, "premium", today());

        assertThat(plan.getPlan().getId(), equalTo("premium"));
        // последний день - сегодня включительно
        assertThat(plan.getValidUntil(), equalTo(today()));
        assertThat(jdbcTemplate.queryForObject("select count(*) from account_subscription where user_id = ?"
                + " and revoked_at is null", Long.class, ALICE), equalTo(1L));
    }

    @Test
    void subscriptionToRemovedPlanFallsBackToDefault() {
        subscription(ALICE, "gone", "now() - interval '1 day'", null);

        assertThat(plan(ALICE).getPlan().getId(), equalTo("free"));
    }

    @Test
    void invalidGrantIsRejected() {
        assertThat(maintenance("PUT", ALICE, body("unknown", null)).statusCode(), equalTo(UNPROCESSABLE));
        // тариф по умолчанию не выдаётся - подписку отзывают
        assertThat(maintenance("PUT", ALICE, body("free", null)).statusCode(), equalTo(UNPROCESSABLE));
        assertThat(maintenance("PUT", ALICE, body("premium", today().minusDays(1))).statusCode(),
                equalTo(UNPROCESSABLE));
        assertThat(maintenance("PUT", ALICE, body("", null)).statusCode(), equalTo(BAD_REQUEST));
        assertThat(maintenance("PUT", ALICE, Map.of("plan", "premium", "comment", "x".repeat(501))).statusCode(),
                equalTo(BAD_REQUEST));
        // без From - 400
        assertThat(send("PUT", "/maintenance/account/" + ALICE + "/plan", null, body("premium", null), false)
                .statusCode(), equalTo(BAD_REQUEST));
        assertThat(send("DELETE", "/maintenance/account/" + ALICE + "/plan", null, null, false).statusCode(),
                equalTo(BAD_REQUEST));
        assertThat(plan(ALICE).isDefaultPlan(), equalTo(true));
    }

    private void alerts(final String userId, final int count) {
        // правила по дате уникальны по облигации: разные тикеры, внешнего ключа на облигацию нет
        jdbcTemplate.update("insert into bond_alert (user_id, ticker, type, days, enabled, created_at)"
                + " select ?, 'T' || n, 'COUPON_BEFORE', 3, true, now() from generate_series(1, ?) n", userId, count);
    }

    /**
     * validFrom, validUntil - SQL-выражения от now(); время подписок - UTC, а сессия JDBC - в поясе JVM, поэтому
     * now() заменяется на UTC
     */
    private void subscription(final String userId, final String plan, final String validFrom,
                              final String validUntil) {
        jdbcTemplate.update(("insert into account_subscription (user_id, plan, source, valid_from, valid_until,"
                + " created_at, created_by) values (?, ?, 'MANUAL', %s, %s, now(), 'TEST')")
                .formatted(utc(validFrom), validUntil == null ? "null" : utc(validUntil)), userId, plan);
    }

    private static String utc(final String expression) {
        return expression.replace("now()", "(now() at time zone 'utc')");
    }

    private HttpResponse<String> createAlert(final String userId, final String type) {
        return send("POST", "/alerts", userId, Map.of("ticker", OFZ, "type", type, "days", 3));
    }

    private HttpResponse<String> createPosition(final String userId) {
        return send("POST", "/portfolio/positions", userId, Map.of("ticker", OFZ, "quantity", 1, "buyPrice", 100));
    }

    private CalendarLinkDto calendarLink(final String userId) {
        final HttpResponse<String> response = send("POST", "/portfolio/calendar/link", userId, null);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), CalendarLinkDto.class);
    }

    private AccountPlanDto plan(final String userId) {
        final HttpResponse<String> response = send("GET", "/account/plan", userId, null);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), AccountPlanDto.class);
    }

    private AccountPlanDto grant(final String userId, final String plan, final LocalDate validUntil) {
        final HttpResponse<String> response = maintenance("PUT", userId, body(plan, validUntil));
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        final AccountPlanDto granted = jsonMapper.readValue(response.body(), AccountPlanDto.class);
        // ответ выдачи - то же, что видит пользователь
        assertThat(plan(userId), equalTo(granted));
        return granted;
    }

    private static Map<String, Object> body(final String plan, final LocalDate validUntil) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan", plan);
        if (validUntil != null) {
            body.put("validUntil", validUntil.toString());
        }
        body.put("comment", "test");
        return body;
    }

    private static PlanLimitUsageDto usage(final AccountPlanDto plan, final PlanLimitDto limit) {
        return plan.getLimits().stream().filter(usage -> usage.getLimit() == limit).findFirst().orElseThrow();
    }

    private static LocalDate today() {
        return LocalDate.now(MOSCOW);
    }

    private HttpResponse<String> maintenance(final String method, final String userId, final Object body) {
        return send(method, "/maintenance/account/" + userId + "/plan", null, body, true);
    }

    private HttpResponse<String> send(final String method, final String path, final String userId,
                                      final Object body) {
        return send(method, path, userId, body, false);
    }

    @SneakyThrows
    private HttpResponse<String> send(final String method, final String path, final String userId,
                                      final Object body, final boolean from) {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Content-Type", "application/json")
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)));
        if (userId != null) {
            request.header(USER_ID_HEADER, userId);
        }
        if (from) {
            request.header("From", "tester");
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
