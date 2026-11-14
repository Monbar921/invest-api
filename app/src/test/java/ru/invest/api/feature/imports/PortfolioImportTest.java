package ru.invest.api.feature.imports;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;
import ru.invest.api.cb.rf.supplier.model.CurrencyDto;
import ru.invest.api.dto.portfolio.PortfolioDto;
import ru.invest.api.dto.portfolio.imports.BrokerConnectionDto;
import ru.invest.api.dto.portfolio.imports.ImportItemStatusDto;
import ru.invest.api.dto.portfolio.imports.ImportStatusDto;
import ru.invest.api.dto.portfolio.imports.PortfolioImportDto;
import ru.invest.api.dto.portfolio.imports.PortfolioImportItemDto;
import ru.invest.api.imports.model.Money;
import ru.invest.api.imports.tinvest.TinvestAccount;
import ru.invest.api.imports.tinvest.TinvestClient;
import ru.invest.api.imports.tinvest.TinvestClientException;
import ru.invest.api.imports.tinvest.TinvestOperation;
import ru.invest.api.imports.tinvest.TinvestPosition;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Импорт от брокера через REST: файл CSV и HTML-отчёт Сбербанка, токен T-Invest (клиент замокан). Готово, когда:
 * повторный импорт того же отчёта ничего не меняет; облигации, которых нет в системе, показаны в отчёте импорта;
 * токен нельзя прочитать из БД и логов. Даты - от сегодняшнего дня UTC.
 */
@ExtendWith(OutputCaptureExtension.class)
@Sql(scripts = "/sql/bonds-for-controller.sql", statements = "truncate table portfolio_import_item, portfolio_import, "
        + "broker_connection, portfolio_sale, portfolio_position, portfolio_broker, account_subscription, amortization, "
        + "currency_rate restart identity cascade")
public class PortfolioImportTest extends AbstractInvestApplicationTest {
    private static final String BASE_URL = "http://localhost:8099/internal/rest/portfolio";
    private static final String USER_ID_HEADER = "X-User-Id";
    // premium - импорт есть в тарифе; free - нет
    private static final String ALICE = "alice-sub";
    private static final String BOB = "bob-sub";
    private static final String CAROL = "carol-sub";
    // bonds-for-controller.sql: ОФЗ, номинал 1000 ₽, погашение в 2041
    private static final String OFZ = "SU26238RMFS4";
    private static final String OFZ_ISIN = "RU000A1038V6";
    private static final String OFZ_UID = "1c0a2f3e-0001-4000-8000-000000000001";
    private static final String AUTO_FINANCE = "RU000A10EQ34";
    // ISIN по формату, но такой облигации в системе нет
    private static final String UNKNOWN_ISIN = "RU000A0JX0J2";
    private static final String TOKEN = "t.ReadOnlyTokenForImportTest-0123456789abcdefXYZ9";
    private static final String ACCOUNT = "2000123456";
    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int NO_CONTENT = 204;
    private static final int UNAUTHORIZED = 401;
    private static final int PAYMENT_REQUIRED = 402;
    private static final int NOT_FOUND = 404;
    private static final int UNPROCESSABLE = 422;
    private static final int BAD_GATEWAY = 502;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CbRfClient cbRfClient;
    @Autowired
    private TinvestClient tinvestClient;
    @Autowired
    private List<CacheManager> cacheManagers;

    @BeforeEach
    void setUp() {
        cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames()
                .forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)));
        final CurrencyDto rates = new CurrencyDto();
        rates.setCurrencies(List.of());
        when(cbRfClient.getCurrencyRates(any())).thenReturn(rates);
        reset(tinvestClient);
        premium(ALICE);
        premium(CAROL);
    }

    @Test
    void requestsWithoutUserAreRejected() {
        assertThat(send("GET", "/imports", null, null).statusCode(), equalTo(UNAUTHORIZED));
        assertThat(send("PUT", "/imports/tinvest", null, Map.of("token", TOKEN)).statusCode(), equalTo(UNAUTHORIZED));
    }

    @Test
    void importNeedsPlanWithFeature() {
        final HttpResponse<String> response = upload(BOB, "CSV", csv(row(today.minusDays(5), "Покупка", OFZ_ISIN,
                "1", "60")));
        assertThat(response.body(), response.statusCode(), equalTo(PAYMENT_REQUIRED));
        assertThat(jsonMapper.readTree(response.body()).get("feature").asString(), equalTo("BROKER_IMPORT"));
        assertThat(send("PUT", "/imports/tinvest", BOB, Map.of("token", TOKEN)).statusCode(),
                equalTo(PAYMENT_REQUIRED));
        // статус подключения и список импортов - без тарифа
        assertThat(send("GET", "/imports/tinvest", BOB, null).statusCode(), equalTo(OK));
        assertThat(send("GET", "/imports", BOB, null).statusCode(), equalTo(OK));
    }

    /**
     * Готово, когда: повторный импорт того же отчёта ничего не меняет; облигации, которых нет в системе, показаны в
     * отчёте импорта, а не теряются.
     */
    @Test
    void csvImportAndRepeatChangesNothing() {
        final LocalDate bought = today.minusDays(20);
        final LocalDate sold = today.minusDays(5);
        final String file = csv(
                row(bought, "Покупка", OFZ_ISIN, "10", "60,5", "12,30", "1,50", "Мой брокер"),
                row(sold, "Продажа", OFZ_ISIN, "4", "61", "5", "0,6", "Мой брокер"),
                row(bought, "Покупка", UNKNOWN_ISIN, "3", "99", "", "", ""),
                row(bought, "Обмен", OFZ_ISIN, "1", "60", "", "", ""));

        final PortfolioImportDto preview = preview(ALICE, "CSV", file);
        assertThat(preview.getStatus(), equalTo(ImportStatusDto.PREVIEW));
        assertThat(preview.getCounts(), equalTo(Map.of(ImportItemStatusDto.NEW, 2, ImportItemStatusDto.UNKNOWN_BOND,
                1, ImportItemStatusDto.INVALID, 1)));
        final PortfolioImportItemDto buy = preview.getItems().getFirst();
        assertThat(buy.getTicker(), equalTo(OFZ));
        assertThat(buy.getName(), equalTo("ОФЗ 26238"));
        assertThat(buy.getPrice(), comparesEqualTo(new BigDecimal("60.5")));
        assertThat(buy.getAci(), comparesEqualTo(new BigDecimal("12.30")));
        assertThat(buy.getCommission(), comparesEqualTo(new BigDecimal("1.50")));
        assertThat(buy.isSelectable(), equalTo(true));
        // не потерялась: и строкой, и в списке неизвестных облигаций
        assertThat(preview.getUnknownBonds(), hasSize(1));
        assertThat(preview.getUnknownBonds().getFirst().getIsin(), equalTo(UNKNOWN_ISIN));
        assertThat(preview.getItems().get(2).isSelectable(), equalTo(false));
        assertThat(preview.getItems().get(3).getMessage(), containsString("Обмен"));
        // предпросмотр портфель не меняет
        assertThat(count("portfolio_position"), equalTo(0));

        final PortfolioImportDto report = apply(ALICE, preview, allIds(preview));
        assertThat(report.getStatus(), equalTo(ImportStatusDto.APPLIED));
        assertThat(report.getCounts().get(ImportItemStatusDto.CREATED), equalTo(2));
        assertThat(report.getItems().getFirst().getTradeId(), notNullValue());
        assertThat(report.getUnknownBonds(), hasSize(1));
        final PortfolioDto portfolio = portfolio(ALICE);
        assertThat(portfolio.getGroups().getFirst().getTicker(), equalTo(OFZ));
        assertThat(portfolio.getGroups().getFirst().getQuantity(), equalTo(6));
        assertThat(portfolio.getGroups().getFirst().getPositions().getFirst().getAci(),
                comparesEqualTo(new BigDecimal("12.30")));

        // тот же файл ещё раз: обе сделки уже импортированы, подтверждение ничего не создаёт
        final PortfolioImportDto again = preview(ALICE, "CSV", file);
        assertThat(again.getCounts().get(ImportItemStatusDto.DUPLICATE), equalTo(2));
        assertThat(again.getCounts().get(ImportItemStatusDto.NEW), nullValue());
        final PortfolioImportDto againReport = apply(ALICE, again, allIds(again));
        assertThat(againReport.getCounts().get(ImportItemStatusDto.CREATED), nullValue());
        assertThat(count("portfolio_position"), equalTo(1));
        assertThat(count("portfolio_sale"), equalTo(1));
        assertThat(portfolio(ALICE).getGroups().getFirst().getQuantity(), equalTo(6));

        // повторное подтверждение - 422, чужой импорт - 404
        assertThat(send("POST", "/imports/" + preview.getId() + "/apply", ALICE,
                Map.of("itemIds", allIds(preview))).statusCode(), equalTo(UNPROCESSABLE));
        assertThat(send("GET", "/imports/" + preview.getId(), CAROL, null).statusCode(), equalTo(NOT_FOUND));
        assertThat(send("POST", "/imports/" + preview.getId() + "/apply", CAROL,
                Map.of("itemIds", Set.of())).statusCode(), equalTo(NOT_FOUND));
        // список - новые первыми, без строк
        final JsonNode list = jsonMapper.readTree(send("GET", "/imports", ALICE, null).body());
        assertThat(list.size(), equalTo(2));
        assertThat(list.get(0).get("id").asLong(), equalTo(again.getId()));
        assertThat(list.get(0).get("items").isNull(), equalTo(true));
    }

    /**
     * Похожая покупка, введённая вручную, не выбрана по умолчанию; продажа, которой не хватает покупок, не создаётся,
     * но остальные сделки создаются.
     */
    @Test
    void manualDuplicateAndFailedSale() {
        final LocalDate bought = today.minusDays(30);
        jdbcTemplate.update("insert into portfolio_position (user_id, ticker, quantity, buy_price, buy_date, created_at)"
                + " values (?, ?, 5, 60, ?, now())", ALICE, OFZ, bought);
        final PortfolioImportDto preview = preview(ALICE, "CSV", csv(
                row(bought, "buy", OFZ_ISIN, "5", "60", "", "", ""),
                row(today.minusDays(10), "sell", AUTO_FINANCE, "2", "100", "", "", ""),
                row(today.minusDays(10), "buy", OFZ, "1", "61", "", "", "")));
        assertThat(preview.getItems().getFirst().getStatus(), equalTo(ImportItemStatusDto.POSSIBLE_DUPLICATE));
        assertThat(preview.getItems().getFirst().getMessage(), containsString("вручную"));
        // по тикеру тоже находится
        assertThat(preview.getItems().get(2).getStatus(), equalTo(ImportItemStatusDto.NEW));

        final PortfolioImportDto report = apply(ALICE, preview, Set.of(preview.getItems().get(1).getId(),
                preview.getItems().get(2).getId()));
        assertThat(report.getItems().getFirst().getStatus(), equalTo(ImportItemStatusDto.SKIPPED));
        assertThat(report.getItems().get(1).getStatus(), equalTo(ImportItemStatusDto.FAILED));
        assertThat(report.getItems().get(1).getMessage(), containsString("Не хватает облигаций"));
        assertThat(report.getItems().get(2).getStatus(), equalTo(ImportItemStatusDto.CREATED));
        assertThat(count("portfolio_position"), equalTo(2));
        assertThat(count("portfolio_sale"), equalTo(0));
    }

    @Test
    void wrongFileIsRejected() {
        final HttpResponse<String> response = upload(ALICE, "CSV", "a;b;c\n1;2;3\n");
        assertThat(response.statusCode(), equalTo(UNPROCESSABLE));
        assertThat(jsonMapper.readTree(response.body()).get("detail").asString(), containsString("дата"));
        assertThat(upload(ALICE, "SBER_HTML", "<html><body><p>нет таблицы</p></body></html>").statusCode(),
                equalTo(UNPROCESSABLE));
    }

    /**
     * Отчёт Сбербанка: таблица сделок по заголовкам, комиссии складываются, РЕПО и итоги пропускаются; файл в
     * Windows-1251.
     */
    @Test
    void sberHtmlReport() {
        final String date = RU_DATE.format(today.minusDays(3));
        final String html = """
                <html><head><meta charset="windows-1251"></head><body>
                <p>Сделки купли/продажи ценных бумаг</p>
                <table>
                <tr><th>Дата заключения</th><th>Дата расчетов</th><th>Наименование ЦБ</th><th>Код ЦБ (ISIN)</th>
                <th>Валюта</th><th>Вид</th><th>Количество, шт.</th><th>Цена</th><th>Сумма</th><th>НКД</th>
                <th>Комиссия Банка за расчет по сделке</th><th>Комиссия Торговой системы</th><th>Номер сделки</th></tr>
                <tr><td>%1$s</td><td>%1$s</td><td>ОФЗ 26238</td><td>%2$s</td><td>RUB</td><td>Покупка</td>
                <td>7</td><td>60,25</td><td>4 217,50</td><td>15,40</td><td>2,11</td><td>0,42</td><td>98765</td></tr>
                <tr><td>%1$s</td><td>%1$s</td><td>ОФЗ 26238</td><td>%2$s</td><td>RUB</td><td>РЕПО 1 часть</td>
                <td>7</td><td>60</td><td>4 200</td><td>0</td><td>0</td><td>0</td><td>98766</td></tr>
                <tr><td colspan="8">Итого</td><td>8 417,50</td></tr>
                </table></body></html>
                """.formatted(date, OFZ_ISIN);
        final HttpResponse<String> response = upload(ALICE, "SBER_HTML", html.getBytes(Charset.forName("windows-1251")));
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        final PortfolioImportDto preview = jsonMapper.readValue(response.body(), PortfolioImportDto.class);
        assertThat(preview.getItems(), hasSize(1));
        assertThat(preview.getSkippedOperations(), equalTo(1));
        final PortfolioImportItemDto item = preview.getItems().getFirst();
        assertThat(item.getStatus(), equalTo(ImportItemStatusDto.NEW));
        assertThat(item.getTicker(), equalTo(OFZ));
        assertThat(item.getQuantity(), equalTo(7));
        assertThat(item.getPrice(), comparesEqualTo(new BigDecimal("60.25")));
        assertThat(item.getAci(), comparesEqualTo(new BigDecimal("15.40")));
        assertThat(item.getCommission(), comparesEqualTo(new BigDecimal("2.53")));
        assertThat(item.getBroker(), equalTo("Сбербанк"));
    }

    /** Токен с правами на торговлю не сохраняется */
    @Test
    void tokenWithTradingRightsIsRejected() {
        when(tinvestClient.getAccounts(TOKEN)).thenReturn(List.of(
                account(ACCOUNT, TinvestAccount.Access.READ_ONLY),
                account("2000999999", TinvestAccount.Access.FULL_ACCESS)));
        final HttpResponse<String> response = send("PUT", "/imports/tinvest", ALICE, Map.of("token", TOKEN));
        assertThat(response.statusCode(), equalTo(UNPROCESSABLE));
        assertThat(jsonMapper.readTree(response.body()).get("detail").asString(), containsString("Только для чтения"));
        assertThat(count("broker_connection"), equalTo(0));

        assertThat(send("PUT", "/imports/tinvest", ALICE, Map.of("token", "short")).statusCode(),
                equalTo(UNPROCESSABLE));
    }

    @Test
    void tinvestErrors() {
        when(tinvestClient.getAccounts(TOKEN)).thenThrow(new TinvestClientException(
                TinvestClientException.Kind.UNAUTHENTICATED, "T-Invest API: UNAUTHENTICATED 40003"));
        assertThat(send("PUT", "/imports/tinvest", ALICE, Map.of("token", TOKEN)).statusCode(),
                equalTo(UNPROCESSABLE));
        reset(tinvestClient);
        when(tinvestClient.getAccounts(TOKEN)).thenThrow(new TinvestClientException(
                TinvestClientException.Kind.UNAVAILABLE, "T-Invest API: UNAVAILABLE"));
        assertThat(send("PUT", "/imports/tinvest", ALICE, Map.of("token", TOKEN)).statusCode(), equalTo(BAD_GATEWAY));
        // без подключённого токена предпросмотра нет
        assertThat(send("POST", "/imports/tinvest/preview", ALICE, Map.of()).statusCode(), equalTo(UNPROCESSABLE));
    }

    /**
     * Готово, когда: токен нельзя прочитать из БД и логов. Операции и позиции T-Invest превращаются в сделки, повторный
     * импорт не создаёт дублей.
     */
    @Test
    void tinvestImport(final CapturedOutput output) {
        when(tinvestClient.getAccounts(TOKEN)).thenReturn(List.of(account(ACCOUNT, TinvestAccount.Access.READ_ONLY)));
        final Instant boughtAt = today.minusDays(40).atTime(9, 0).toInstant(ZoneOffset.UTC);
        final Instant soldAt = today.minusDays(10).atTime(12, 0).toInstant(ZoneOffset.UTC);
        when(tinvestClient.getTrades(eq(TOKEN), eq(ACCOUNT), isNull(), anyInt())).thenReturn(List.of(
                new TinvestOperation("op-1", ACCOUNT, TinvestOperation.Side.BUY, true, OFZ_UID, "BBG00OFZ0001",
                        "ОФЗ 26238", boughtAt, 10, rub("600"), rub("6000"), rub("20"), rub("3"), List.of("111")),
                new TinvestOperation("op-2", ACCOUNT, TinvestOperation.Side.SELL, true, OFZ_UID, "BBG00OFZ0001",
                        "ОФЗ 26238", soldAt, 2, rub("615"), rub("1230"), rub("4"), rub("0.62"), List.of("112")),
                new TinvestOperation("op-3", ACCOUNT, TinvestOperation.Side.BUY, false, "share-uid", "BBG000SHARE1",
                        "Акция", boughtAt, 1, rub("300"), rub("300"), null, rub("0.1"), List.of("113")),
                new TinvestOperation("op-4", ACCOUNT, TinvestOperation.Side.BUY, true, "unknown-uid", "BBG000UNKN01",
                        "Неизвестная облигация", boughtAt, 1, rub("990"), rub("990"), null, null, List.of("114"))));
        // у брокера 10 шт., а по сделкам 8 - 2 шт. без истории
        when(tinvestClient.getBondPositions(TOKEN, ACCOUNT)).thenReturn(List.of(
                new TinvestPosition(OFZ_UID, "BBG00OFZ0001", OFZ, BigDecimal.TEN, rub("605"))));

        final HttpResponse<String> connected = send("PUT", "/imports/tinvest", ALICE, Map.of("token", TOKEN));
        assertThat(connected.body(), connected.statusCode(), equalTo(OK));
        assertThat(connected.body(), not(containsString(TOKEN)));
        final BrokerConnectionDto connection = jsonMapper.readValue(connected.body(), BrokerConnectionDto.class);
        assertThat(connection.isConnected(), equalTo(true));
        assertThat(connection.getTokenHint(), equalTo("XYZ9"));
        assertThat(connection.getAccounts(), hasSize(1));
        // в БД - только шифртекст
        final String stored = jdbcTemplate.queryForObject("select token_encrypted from broker_connection where user_id"
                + " = ?", String.class, ALICE);
        assertThat(stored, startsWith("v1:"));
        assertThat(stored, not(containsString(TOKEN)));
        assertThat(jdbcTemplate.queryForList("select * from broker_connection").toString(), not(containsString(TOKEN)));

        final HttpResponse<String> response = send("POST", "/imports/tinvest/preview", ALICE, Map.of());
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        final PortfolioImportDto preview = jsonMapper.readValue(response.body(), PortfolioImportDto.class);
        assertThat(preview.getCounts(), equalTo(Map.of(ImportItemStatusDto.NEW, 2, ImportItemStatusDto.UNKNOWN_BOND, 1,
                ImportItemStatusDto.NEEDS_REVIEW, 1)));
        assertThat(preview.getSkippedOperations(), equalTo(1));
        final PortfolioImportItemDto buy = preview.getItems().getFirst();
        // 600 ₽ при номинале 1000 ₽
        assertThat(buy.getPrice(), comparesEqualTo(new BigDecimal("60")));
        assertThat(buy.getCommission(), comparesEqualTo(new BigDecimal("3.00")));
        assertThat(buy.getAci(), comparesEqualTo(new BigDecimal("20.00")));
        assertThat(buy.getBroker(), equalTo("Т-Инвестиции"));
        assertThat(buy.getDate(), equalTo(today.minusDays(40)));
        assertThat(preview.getItems().get(1).getPrice(), comparesEqualTo(new BigDecimal("61.5")));
        assertThat(preview.getUnknownBonds().getFirst().getFigi(), equalTo("BBG000UNKN01"));
        assertThat(preview.getUnknownBonds().getFirst().getName(), equalTo("Неизвестная облигация"));
        final PortfolioImportItemDto rest = preview.getItems().get(3);
        assertThat(rest.getStatus(), equalTo(ImportItemStatusDto.NEEDS_REVIEW));
        assertThat(rest.getQuantity(), equalTo(2));
        assertThat(rest.getPrice(), comparesEqualTo(new BigDecimal("60.5")));
        assertThat(rest.getDate(), equalTo(LocalDate.of(2020, 1, 15)));

        final Set<Long> newIds = preview.getItems().stream()
                .filter(item -> item.getStatus() == ImportItemStatusDto.NEW)
                .map(PortfolioImportItemDto::getId)
                .collect(Collectors.toSet());
        final PortfolioImportDto report = apply(ALICE, preview, newIds);
        assertThat(report.getCounts().get(ImportItemStatusDto.CREATED), equalTo(2));
        assertThat(report.getItems().get(3).getStatus(), equalTo(ImportItemStatusDto.SKIPPED));
        assertThat(portfolio(ALICE).getGroups().getFirst().getQuantity(), equalTo(8));

        // повторный импорт: сделки - уже импортированы, остаток - снова на проверку
        final PortfolioImportDto again = jsonMapper.readValue(send("POST", "/imports/tinvest/preview", ALICE,
                Map.of()).body(), PortfolioImportDto.class);
        assertThat(again.getCounts().get(ImportItemStatusDto.DUPLICATE), equalTo(2));
        assertThat(again.getCounts().get(ImportItemStatusDto.NEEDS_REVIEW), equalTo(1));
        assertThat(again.getCounts().get(ImportItemStatusDto.NEW), nullValue());

        final BrokerConnectionDto status = jsonMapper.readValue(send("GET", "/imports/tinvest", ALICE, null).body(),
                BrokerConnectionDto.class);
        assertThat(status.isEnabled(), equalTo(true));
        assertThat(status.getLastImportAt(), notNullValue());
        assertThat(status.getAccounts(), nullValue());

        // токена нет ни в одном сообщении лога
        assertThat(output.getAll(), not(containsString(TOKEN)));

        assertThat(send("DELETE", "/imports/tinvest", ALICE, null).statusCode(), equalTo(NO_CONTENT));
        assertThat(count("broker_connection"), equalTo(0));
        assertThat(send("POST", "/imports/tinvest/preview", ALICE, Map.of()).statusCode(), equalTo(UNPROCESSABLE));
    }

    private static TinvestAccount account(final String id, final TinvestAccount.Access access) {
        return new TinvestAccount(id, "Брокерский счёт", "ACCOUNT_TYPE_TINKOFF", LocalDate.of(2020, 1, 15), access,
                false);
    }

    private static Money rub(final String amount) {
        return new Money(new BigDecimal(amount), "rub");
    }

    private void premium(final String userId) {
        jdbcTemplate.update("insert into account_subscription (user_id, plan, source, valid_from, valid_until,"
                + " created_at, created_by) values (?, 'premium', 'MANUAL', now() - interval '1 day', null, now(),"
                + " 'TEST')", userId);
    }

    private static String csv(final String... rows) {
        return "Дата;Операция;ISIN;Количество;Цена, %;НКД;Комиссия;Брокер\n" + String.join("\n", rows) + "\n";
    }

    private static String row(final LocalDate date, final String operation, final String code,
                              final String... values) {
        return RU_DATE.format(date) + ";" + operation + ";" + code + ";" + String.join(";", values);
    }

    private PortfolioImportDto preview(final String userId, final String format, final String content) {
        final HttpResponse<String> response = upload(userId, format, content);
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        return jsonMapper.readValue(response.body(), PortfolioImportDto.class);
    }

    private PortfolioImportDto apply(final String userId, final PortfolioImportDto preview, final Set<Long> ids) {
        final HttpResponse<String> response = send("POST", "/imports/" + preview.getId() + "/apply", userId,
                Map.of("itemIds", ids));
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), PortfolioImportDto.class);
    }

    private static Set<Long> allIds(final PortfolioImportDto preview) {
        return preview.getItems().stream().map(PortfolioImportItemDto::getId).collect(Collectors.toSet());
    }

    private PortfolioDto portfolio(final String userId) {
        final HttpResponse<String> response = send("GET", "", userId, null);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), PortfolioDto.class);
    }

    private int count(final String table) {
        return Optional.ofNullable(jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class))
                .orElse(0);
    }

    private HttpResponse<String> upload(final String userId, final String format, final String content) {
        return upload(userId, format, content.getBytes(StandardCharsets.UTF_8));
    }

    @SneakyThrows
    private HttpResponse<String> upload(final String userId, final String format, final byte[] content) {
        final String boundary = "----invest-test-boundary";
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"report\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + "/imports/file/preview?format="
                        + format))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (userId != null) {
            request.header(USER_ID_HEADER, userId);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
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
}
