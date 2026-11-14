package ru.invest.api.feature.alert;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.alert.service.usecase.FilterAlertCheckUseCase;
import ru.invest.api.dto.alert.AlertDto;
import ru.invest.api.dto.alert.AlertTypeDto;
import ru.invest.api.dto.alert.NotificationDto;
import ru.invest.api.dto.filter.BondScopeDto;
import ru.invest.api.dto.filter.SavedFilterDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/**
 * Сохранённые фильтры и уведомления о новых облигациях в выборке. Облигации - bonds-for-controller.sql; выборка
 * задаётся доходностью к погашению, которую тест меняет SQL (у остальных облигаций доходности нет - фильтр по ней
 * их не пропускает), кэш облигаций сбрасывается - как после синхронизации цен.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql", statements = {
        "truncate table notification, bond_alert, saved_filter, saved_filter_match restart identity",
        "truncate table account_auto_renewal, payment, account_subscription restart identity",
        "update coupon set yield_to_maturity = null"})
public class SavedFilterTest extends AbstractInvestApplicationTest {
    private static final String FILTERS_URL = "http://localhost:8099/internal/rest/alerts/filters";
    private static final String ALERTS_URL = "http://localhost:8099/internal/rest/alerts";
    private static final String ALICE = "alice-sub";
    private static final String BOB = "bob-sub";
    private static final String AUTO = "RU000A10EQ34";
    private static final String HYDRO = "RU000A10EHC1";
    private static final Map<String, Object> YIELD_FROM_20 = Map.of("yieldToMaturity", Map.of("min", 20));
    private static final int CREATED = 201;
    private static final int NO_CONTENT = 204;
    private static final int BAD_REQUEST = 400;
    private static final int PAYMENT_REQUIRED = 402;
    private static final int NOT_FOUND = 404;
    private static final int UNPROCESSABLE = 422;
    /** Лимит фильтров бесплатного тарифа в тестовой конфигурации */
    private static final int FREE_FILTERS = 3;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private FilterAlertCheckUseCase filterAlertCheckUseCase;
    @Autowired
    private TinkoffBondCacheRepositoryUseCase bondCache;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void freshCache() {
        bondCache.evictAll();
    }

    @Test
    void createListRenameDelete() {
        setYield(AUTO, "21");
        final SavedFilterDto created = create(Map.of("name", "  Доходные  ", "scope", "LOCAL",
                "parameters", Map.of("yieldToMaturity", Map.of("min", 20),
                        "bondSorts", List.of(Map.of("sortField", "YIELD_TO_MATURITY", "sortOrder", "DESC")))));
        assertThat(created.getName(), equalTo("Доходные"));
        assertThat(created.getScope(), equalTo(BondScopeDto.LOCAL));
        assertThat(created.getParameters().getYieldToMaturity().getMin(), comparesEqualTo(BigDecimal.valueOf(20)));
        assertThat(created.getParameters().getBondSorts().getFirst().getSortField().name(),
                equalTo("YIELD_TO_MATURITY"));
        assertThat(created.isNotify(), equalTo(false));
        assertThat(created.getAlertId(), nullValue());
        assertThat(created.getMatches(), equalTo(1));

        // название уникально без учёта регистра
        assertThat(send("POST", FILTERS_URL, ALICE, Map.of("name", "доходные")).statusCode(), equalTo(UNPROCESSABLE));
        assertThat(send("POST", FILTERS_URL, ALICE, Map.of("name", " ")).statusCode(), equalTo(BAD_REQUEST));
        assertThat(send("POST", FILTERS_URL, ALICE, Map.of("name", "Рейтинг",
                "parameters", Map.of("creditRating", Map.of("min", "XYZ")))).statusCode(), equalTo(BAD_REQUEST));
        // у другого пользователя то же название можно, а чужой фильтр не виден
        assertThat(send("POST", FILTERS_URL, BOB, Map.of("name", "Доходные")).statusCode(), equalTo(CREATED));
        assertThat(send("GET", FILTERS_URL + "/" + created.getId(), BOB, null).statusCode(), equalTo(NOT_FOUND));

        final SavedFilterDto renamed = update(created.getId(), Map.of("name", "ВДО"));
        assertThat(renamed.getName(), equalTo("ВДО"));
        assertThat(renamed.getParameters().getYieldToMaturity().getMin(), comparesEqualTo(BigDecimal.valueOf(20)));
        assertThat(filters(ALICE).stream().map(SavedFilterDto::getName).toList(), contains("ВДО"));

        assertThat(send("DELETE", FILTERS_URL + "/" + created.getId(), ALICE, null).statusCode(),
                equalTo(NO_CONTENT));
        assertThat(filters(ALICE), hasSize(0));
    }

    @Test
    void filtersAreLimitedByPlan() {
        for (int i = 0; i < FREE_FILTERS; i++) {
            create(Map.of("name", "Фильтр " + i));
        }
        final HttpResponse<String> rejected = send("POST", FILTERS_URL, ALICE, Map.of("name", "Ещё один"));
        assertThat(rejected.statusCode(), equalTo(PAYMENT_REQUIRED));
        assertThat(jsonMapper.readTree(rejected.body()).get("limit").asString(), equalTo("SAVED_FILTERS"));
    }

    @Test
    void notifiesOnceAboutBondEnteringSelection() {
        setYield(AUTO, "21");
        final SavedFilterDto filter = create(Map.of("name", "Доходные", "parameters", YIELD_FROM_20,
                "notify", true));
        assertThat(filter.isNotify(), equalTo(true));
        // то, что подходило при включении, - отправная точка, не уведомление
        assertThat(check(), equalTo(0));
        assertThat(notifications(), hasSize(0));

        setYield(HYDRO, "22.5");
        assertThat(check(), equalTo(1));
        assertThat(notifications().getFirst().getText(), equalTo("""
                Фильтр «Доходные»: в выборку вошла 1 облигация
                Гидромашсервис 002Р-01 (RU000A10EHC1): 95,05% от номинала, доходность 22,5%"""));
        assertThat(notifications().getFirst().getAlertId(), equalTo(filter.getAlertId()));
        // следующая проверка без изменений - без повторов
        assertThat(check(), equalTo(0));
    }

    @Test
    void oscillationAroundBoundaryDoesNotRepeat() {
        create(Map.of("name", "Доходные", "parameters", YIELD_FROM_20, "notify", true));

        setYield(AUTO, "20.05");
        assertThat(check(), equalTo(1));
        // вышла за границу, но не дальше запаса 0,1 п.п. - и вернулась: уведомления нет
        setYield(AUTO, "19.95");
        assertThat(check(), equalTo(0));
        setYield(AUTO, "20.02");
        assertThat(check(), equalTo(0));
        // ушла дальше запаса - при новом входе новое уведомление
        setYield(AUTO, "19.85");
        assertThat(check(), equalTo(0));
        setYield(AUTO, "20.3");
        assertThat(check(), equalTo(1));
        assertThat(notifications(), hasSize(2));
    }

    @Test
    void manyNewBondsGiveOneNotification() {
        create(Map.of("name", "Доходные", "parameters", YIELD_FROM_20, "notify", true));
        setYield(AUTO, "21");
        setYield(HYDRO, "22");

        assertThat(check(), equalTo(1));
        final String text = notifications().getFirst().getText();
        assertThat(text.lines().toList(), hasSize(3));
        assertThat(text.lines().findFirst().orElseThrow(), equalTo("Фильтр «Доходные»: в выборку вошли 2 облигации"));
    }

    @Test
    void notificationToggleAndAlertRule() {
        setYield(AUTO, "21");
        final SavedFilterDto filter = create(Map.of("name", "Доходные", "parameters", YIELD_FROM_20));
        final SavedFilterDto enabled = update(filter.getId(), Map.of("notify", true));
        assertThat(enabled.isNotify(), equalTo(true));

        // правило видно среди правил уведомлений, с названием фильтра; создать его через /alerts нельзя
        final AlertDto alert = alerts().getFirst();
        assertThat(alert.getType(), equalTo(AlertTypeDto.SAVED_FILTER));
        assertThat(alert.getFilterId(), equalTo(filter.getId()));
        assertThat(alert.getFilterName(), equalTo("Доходные"));
        assertThat(alert.getTicker(), nullValue());
        assertThat(send("POST", ALERTS_URL, ALICE, Map.of("type", "SAVED_FILTER")).statusCode(),
                equalTo(UNPROCESSABLE));

        // выключено как правило (кнопка в Telegram) - у фильтра уведомления выключены, правило осталось
        send("PUT", ALERTS_URL + "/" + alert.getId(), ALICE, Map.of("enabled", false));
        assertThat(filters(ALICE).getFirst().isNotify(), equalTo(false));
        assertThat(filters(ALICE).getFirst().getAlertId(), equalTo(alert.getId()));
        setYield(HYDRO, "22");
        assertThat(check(), equalTo(0));
        // включено заново - вошедшее за время выключения стало отправной точкой
        update(filter.getId(), Map.of("notify", true));
        assertThat(check(), equalTo(0));
        assertThat(notifications(), hasSize(0));

        // выключено у фильтра - правило удалено
        final SavedFilterDto disabled = update(filter.getId(), Map.of("notify", false));
        assertThat(disabled.isNotify(), equalTo(false));
        assertThat(disabled.getAlertId(), nullValue());
        assertThat(alerts(), hasSize(0));
    }

    @Test
    void newParametersStartFromCurrentSelection() {
        setYield(AUTO, "21");
        final SavedFilterDto filter = create(Map.of("name", "Доходные", "parameters", YIELD_FROM_20,
                "notify", true));
        setYield(HYDRO, "15");

        // под новые параметры подходит и HYDRO, но он подходил уже в момент изменения - не новость
        update(filter.getId(), Map.of("parameters", Map.of("yieldToMaturity", Map.of("min", 10))));
        assertThat(check(), equalTo(0));
        assertThat(notifications(), hasSize(0));
    }

    @Test
    void deletingFilterKeepsHistory() {
        final SavedFilterDto filter = create(Map.of("name", "Доходные", "parameters", YIELD_FROM_20,
                "notify", true));
        setYield(AUTO, "21");
        assertThat(check(), equalTo(1));

        send("DELETE", FILTERS_URL + "/" + filter.getId(), ALICE, null);
        assertThat(alerts(), hasSize(0));
        assertThat(notifications(), hasSize(1));
        assertThat(notifications().getFirst().getAlertId(), nullValue());
        assertThat(jdbcTemplate.queryForObject("select count(*) from saved_filter_match", Long.class),
                equalTo(0L));
    }

    @Test
    void notificationsNeedFreeAlertRule() {
        jdbcTemplate.update("""
                insert into bond_alert (user_id, ticker, type, direction, threshold, state, enabled, created_at)
                select ?, ?, 'PRICE_LEVEL', 'UP', 200, 'ARMED', true, now() from generate_series(1, 50)""",
                ALICE, AUTO);
        final SavedFilterDto filter = create(Map.of("name", "Доходные"));

        assertThat(send("PUT", FILTERS_URL + "/" + filter.getId(), ALICE, Map.of("notify", true)).statusCode(),
                equalTo(PAYMENT_REQUIRED));
        // фильтр с notify при создании тоже не создаётся
        assertThat(send("POST", FILTERS_URL, ALICE, Map.of("name", "Другой", "notify", true)).statusCode(),
                equalTo(PAYMENT_REQUIRED));
        assertThat(filters(ALICE), hasSize(1));
    }

    private int check() {
        bondCache.evictAll();
        return filterAlertCheckUseCase.checkAll();
    }

    private void setYield(final String ticker, final String yield) {
        jdbcTemplate.update("update coupon set yield_to_maturity = ? where ticker = ?", new BigDecimal(yield), ticker);
        bondCache.evictAll();
    }

    private SavedFilterDto create(final Map<String, Object> body) {
        final HttpResponse<String> response = send("POST", FILTERS_URL, ALICE, body);
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        return jsonMapper.readValue(response.body(), SavedFilterDto.class);
    }

    private SavedFilterDto update(final long id, final Map<String, Object> body) {
        final HttpResponse<String> response = send("PUT", FILTERS_URL + "/" + id, ALICE, body);
        assertThat(response.body(), response.statusCode(), equalTo(200));
        return jsonMapper.readValue(response.body(), SavedFilterDto.class);
    }

    private List<SavedFilterDto> filters(final String userId) {
        return jsonMapper.readValue(send("GET", FILTERS_URL, userId, null).body(), new TypeReference<>() {
        });
    }

    private List<AlertDto> alerts() {
        return jsonMapper.readValue(send("GET", ALERTS_URL, ALICE, null).body(), new TypeReference<>() {
        });
    }

    private List<NotificationDto> notifications() {
        final PageDto<NotificationDto> page = jsonMapper.readValue(
                send("GET", ALERTS_URL + "/notifications", ALICE, null).body(), new TypeReference<>() {
                });
        return page.getContent();
    }

    @SneakyThrows
    private HttpResponse<String> send(final String method, final String url, final String userId,
                                      final Object body) {
        return httpClient.send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-User-Id", userId)
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body)))
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
