package ru.invest.api.feature.portfolio;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;
import ru.invest.api.cb.rf.supplier.model.CurrencyDto;
import ru.invest.api.dto.portfolio.PortfolioCalendarDto;
import ru.invest.api.dto.portfolio.PortfolioClosedPositionDto;
import ru.invest.api.dto.portfolio.PortfolioDto;
import ru.invest.api.dto.portfolio.PortfolioGroupDto;
import ru.invest.api.dto.portfolio.PortfolioIncomeDto;
import ru.invest.api.dto.portfolio.PortfolioPositionDto;
import ru.invest.api.dto.portfolio.PortfolioTradeDto;
import ru.invest.api.dto.portfolio.PortfolioTradeTypeDto;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Полный доход портфеля через REST: продажи (в том числе частичные), закрытые позиции, сделки, полученные купоны,
 * налог, старые покупки без НКД и комиссии. Даты - от сегодняшнего дня UTC; купоны вставляются SQL.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql", statements = "truncate table portfolio_sale, portfolio_position, "
        + "portfolio_broker, amortization, currency_rate restart identity")
public class PortfolioTradesTest extends AbstractInvestApplicationTest {
    private static final String BASE_URL = "http://localhost:8099/internal/rest/portfolio";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ALICE = "alice-sub";
    private static final String BOB = "bob-sub";
    // bonds-for-controller.sql: ОФЗ, номинал 1000 ₽, цена 61%, coupon id 1, погашение в 2041
    private static final String OFZ = "SU26238RMFS4";
    // номинал 1000 ₽, цена 100,5%
    private static final String AUTO_FINANCE = "RU000A10EQ34";
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int NO_CONTENT = 204;
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int NOT_FOUND = 404;
    private static final int UNPROCESSABLE = 422;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CbRfClient cbRfClient;
    @Autowired
    private List<CacheManager> cacheManagers;

    @BeforeEach
    void setUp() {
        cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames()
                .forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)));
        // в тестах только рублёвые облигации - курс 1 без ЦБ
        final CurrencyDto rates = new CurrencyDto();
        rates.setCurrencies(List.of());
        when(cbRfClient.getCurrencyRates(any())).thenReturn(rates);
    }

    @Test
    void requestsWithoutUserAreRejected() {
        assertThat(send("GET", "/trades", null, null).statusCode(), equalTo(UNAUTHORIZED));
        assertThat(send("POST", "/sales", null, sale(OFZ, 1, "60", today)).statusCode(), equalTo(UNAUTHORIZED));
    }

    /**
     * Готово, когда: старые покупки без комиссии и НКД продолжают работать - НКД считается по графику, комиссия 0.
     */
    @Test
    void oldPurchaseWithoutAciAndCommissionWorks() {
        jdbcTemplate.update("insert into portfolio_position (user_id, ticker, quantity, buy_price, buy_date, created_at)"
                + " values (?, ?, 4, 60, ?, now())", ALICE, OFZ, today.minusDays(100));
        coupon(OFZ, "35", today.minusDays(150));
        coupon(OFZ, "35", today.minusDays(60));
        coupon(OFZ, "35", today.plusDays(30));

        final PortfolioDto portfolio = portfolio(ALICE, null);
        final PortfolioGroupDto group = portfolio.getGroups().getFirst();
        final PortfolioIncomeDto income = group.getIncome();

        assertThat(group.getPositions().getFirst().getAci(), nullValue());
        assertThat(group.getPositions().getFirst().getCommission(), nullValue());
        // купон после покупки × 4
        assertThat(income.getCouponIncome(), comparesEqualTo(new BigDecimal("140")));
        // НКД по графику: 50 из 90 дней купона 35 на облигацию
        assertThat(income.getBuyAci(), comparesEqualTo(new BigDecimal("77.78")));
        assertThat(income.getCommission(), comparesEqualTo(BigDecimal.ZERO));
        // 4 × 1000 × (61 − 60)%
        assertThat(income.getPriceIncome(), comparesEqualTo(new BigDecimal("40")));
        assertThat(income.getFullIncome(), notNullValue());
        assertThat(income.getXirr(), notNullValue());
        assertThat(income.isIncomplete(), equalTo(false));
        assertThat(portfolio.getIncome().getCurrency(), equalTo("rub"));
        assertThat(portfolio.getIncome().getFullIncome(), comparesEqualTo(income.getFullIncome()));
        assertThat(portfolio.getIncomeTotals(), hasSize(1));
        assertThat(trades(ALICE).getFirst().isAciCalculated(), equalTo(true));
    }

    @Test
    void purchaseKeepsAciAndCommission() {
        final Map<String, Object> body = purchase(OFZ, 2, "60", today.minusDays(10));
        body.put("aci", new BigDecimal("12.34"));
        body.put("commission", new BigDecimal("1.50"));
        final HttpResponse<String> response = send("POST", "/positions", ALICE, body);
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        final PortfolioPositionDto position = jsonMapper.readValue(response.body(), PortfolioPositionDto.class);

        assertThat(position.getAci(), comparesEqualTo(new BigDecimal("12.34")));
        assertThat(position.getCommission(), comparesEqualTo(new BigDecimal("1.50")));
        final PortfolioIncomeDto income = portfolio(ALICE, null).getGroups().getFirst().getIncome();
        assertThat(income.getBuyAci(), comparesEqualTo(new BigDecimal("12.34")));
        assertThat(income.getCommission(), comparesEqualTo(new BigDecimal("1.50")));

        body.put("commission", new BigDecimal("-1"));
        assertThat(send("POST", "/positions", ALICE, body).statusCode(), equalTo(BAD_REQUEST));
    }

    /**
     * Частичная продажа уменьшает строку облигации (FIFO), полная - переводит её в закрытые; календарь выплат - на
     * непроданное количество.
     */
    @Test
    void partialAndFullSale() {
        final Long first = buy(ALICE, OFZ, 10, "60", today.minusDays(40));
        final Long second = buy(ALICE, OFZ, 5, "62", today.minusDays(20));
        coupon(OFZ, "35", today.minusDays(30));
        coupon(OFZ, "35", today.plusDays(60));

        final PortfolioTradeDto sale = createSale(ALICE, OFZ, 12, "63", today.minusDays(10));
        assertThat(sale.getType(), equalTo(PortfolioTradeTypeDto.SALE));
        // 12 × 1000 × 63%
        assertThat(sale.getAmount(), comparesEqualTo(new BigDecimal("7560")));
        assertThat(sale.isAciCalculated(), equalTo(true));

        final PortfolioGroupDto group = portfolio(ALICE, null).getGroups().getFirst();
        assertThat(group.getQuantity(), equalTo(3));
        // первая покупка продана целиком, из второй - 2
        assertThat(group.getPositions().stream().map(PortfolioPositionDto::getId).toList(), contains(first, second));
        assertThat(group.getPositions().stream().map(PortfolioPositionDto::getRemainingQuantity).toList(),
                contains(0, 3));
        // средняя цена - по непроданным
        assertThat(group.getBuyPrice(), comparesEqualTo(new BigDecimal("62")));
        // 3 × 1000 × 62%
        assertThat(group.getBuyAmount(), comparesEqualTo(new BigDecimal("1860")));
        // купон 30 дней назад - на 10 облигаций первой покупки
        assertThat(group.getIncome().getCouponIncome(), comparesEqualTo(new BigDecimal("350")));
        assertThat(group.getIncome().getSoldAmount(), comparesEqualTo(new BigDecimal("7560")));
        assertThat(calendar(ALICE).getEvents().getFirst().getQuantity(), equalTo(3L));

        createSale(ALICE, OFZ, 3, "61", today);
        final PortfolioDto portfolio = portfolio(ALICE, null);
        assertThat(portfolio.getGroups(), empty());
        assertThat(portfolio.getTotals(), empty());
        final PortfolioClosedPositionDto closed = portfolio.getClosed().getFirst();
        assertThat(closed.getTicker(), equalTo(OFZ));
        assertThat(closed.getQuantity(), equalTo(15));
        assertThat(closed.getFirstBuyDate(), equalTo(today.minusDays(40)));
        assertThat(closed.getCloseDate(), equalTo(today));
        assertThat(closed.getIncome().getCurrentAmount(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(closed.getIncome().getFullIncome(), notNullValue());
        assertThat(calendar(ALICE).getEvents(), empty());

        // сделки - новые первыми
        assertThat(trades(ALICE).stream().map(PortfolioTradeDto::getType).toList(), contains(
                PortfolioTradeTypeDto.SALE, PortfolioTradeTypeDto.SALE, PortfolioTradeTypeDto.BUY,
                PortfolioTradeTypeDto.BUY));
    }

    @Test
    void saleValidation() {
        final Long purchase = buy(ALICE, OFZ, 10, "60", today.minusDays(20));

        // больше, чем куплено
        assertThat(send("POST", "/sales", ALICE, sale(OFZ, 11, "60", today)).statusCode(), equalTo(UNPROCESSABLE));
        // раньше покупки
        assertThat(send("POST", "/sales", ALICE, sale(OFZ, 1, "60", today.minusDays(30))).statusCode(),
                equalTo(UNPROCESSABLE));
        // в будущем
        assertThat(send("POST", "/sales", ALICE, sale(OFZ, 1, "60", today.plusDays(1))).statusCode(),
                equalTo(UNPROCESSABLE));
        // нет тикера, неизвестная облигация, неверная цена
        assertThat(send("POST", "/sales", ALICE, sale(null, 1, "60", today)).statusCode(), equalTo(UNPROCESSABLE));
        assertThat(send("POST", "/sales", ALICE, sale("UNKNOWN", 1, "60", today)).statusCode(), equalTo(NOT_FOUND));
        assertThat(send("POST", "/sales", ALICE, sale(OFZ, 1, "0", today)).statusCode(), equalTo(BAD_REQUEST));
        // не раньше погашения - облигацию гасит эмитент
        jdbcTemplate.update("update bond set maturity_date = ? where ticker = ?", today.atStartOfDay(), OFZ);
        assertThat(send("POST", "/sales", ALICE, sale(OFZ, 1, "60", today)).statusCode(), equalTo(UNPROCESSABLE));
        jdbcTemplate.update("update bond set maturity_date = ? where ticker = ?",
                today.plusYears(10).atStartOfDay(), OFZ);

        final PortfolioTradeDto sale = createSale(ALICE, OFZ, 8, "61", today.minusDays(5));
        // покупку нельзя уменьшить или удалить, пока продаже не хватает облигаций
        assertThat(send("PUT", "/positions/" + purchase, ALICE, purchase(OFZ, 7, "60", today.minusDays(20)))
                .statusCode(), equalTo(UNPROCESSABLE));
        assertThat(send("PUT", "/positions/" + purchase, ALICE, purchase(OFZ, 10, "60", today.minusDays(3)))
                .statusCode(), equalTo(UNPROCESSABLE));
        assertThat(send("DELETE", "/positions/" + purchase, ALICE, null).statusCode(), equalTo(UNPROCESSABLE));
        // изменить продажу так, что не хватает, - тоже нельзя
        assertThat(send("PUT", "/sales/" + sale.getId(), ALICE, sale(OFZ, 11, "61", today)).statusCode(),
                equalTo(UNPROCESSABLE));

        // чужая продажа не видна
        assertThat(send("PUT", "/sales/" + sale.getId(), BOB, sale(OFZ, 1, "61", today)).statusCode(),
                equalTo(NOT_FOUND));
        assertThat(send("DELETE", "/sales/" + sale.getId(), BOB, null).statusCode(), equalTo(NOT_FOUND));

        final HttpResponse<String> updated = send("PUT", "/sales/" + sale.getId(), ALICE,
                sale(OFZ, 10, "62", today.minusDays(4)));
        assertThat(updated.body(), updated.statusCode(), equalTo(OK));
        assertThat(jsonMapper.readValue(updated.body(), PortfolioTradeDto.class).getQuantity(), equalTo(10));

        assertThat(send("DELETE", "/sales/" + sale.getId(), ALICE, null).statusCode(), equalTo(NO_CONTENT));
        assertThat(send("DELETE", "/positions/" + purchase, ALICE, null).statusCode(), equalTo(NO_CONTENT));
    }

    @Test
    void maturedBondIsRedeemedAutomatically() {
        buy(ALICE, AUTO_FINANCE, 2, "99", today.minusDays(30));
        jdbcTemplate.update("update bond set maturity_date = ? where ticker = ?",
                today.minusDays(1).atStartOfDay(), AUTO_FINANCE);

        final PortfolioDto portfolio = portfolio(ALICE, null);

        assertThat(portfolio.getGroups(), empty());
        assertThat(portfolio.getClosed().getFirst().getCloseDate(), equalTo(today.minusDays(1)));
        // погашен номинал 2 × 1000 - доход от цены 20
        assertThat(portfolio.getClosed().getFirst().getIncome().getSoldAmount(), comparesEqualTo(new BigDecimal("2000")));
        assertThat(portfolio.getClosed().getFirst().getIncome().getPriceIncome(), comparesEqualTo(new BigDecimal("20")));
        final PortfolioTradeDto redemption = trades(ALICE).getFirst();
        assertThat(redemption.getType(), equalTo(PortfolioTradeTypeDto.REDEMPTION));
        assertThat(redemption.getId(), nullValue());
        assertThat(redemption.getQuantity(), equalTo(2));
    }

    @Test
    void taxRateIsAppliedAndValidated() {
        // у ОФЗ в наборе нет графика купонов: ни купонов, ни НКД
        buy(ALICE, OFZ, 10, "59", today.minusDays(30));

        final PortfolioIncomeDto noTax = portfolio(ALICE, null).getGroups().getFirst().getIncome();
        final PortfolioIncomeDto taxed = portfolio(ALICE, "13").getGroups().getFirst().getIncome();

        assertThat(noTax.getTax(), comparesEqualTo(BigDecimal.ZERO));
        assertThat(noTax.getTaxRatePercent(), nullValue());
        // доход от цены 200 (без купонов и НКД) - налог 26
        assertThat(taxed.getProfit(), comparesEqualTo(new BigDecimal("200")));
        assertThat(taxed.getTax(), comparesEqualTo(new BigDecimal("26")));
        assertThat(taxed.getFullIncome(), comparesEqualTo(new BigDecimal("174")));
        assertThat(send("GET", "?taxRatePercent=150", ALICE, null).statusCode(), equalTo(UNPROCESSABLE));
    }

    @Test
    void bondMissingFromSystemHasNoIncome() {
        jdbcTemplate.update("insert into portfolio_position (user_id, ticker, quantity, buy_price, buy_date, created_at)"
                + " values (?, 'GONE', 1, 100, ?, now())", ALICE, today.minusDays(5));
        buy(ALICE, AUTO_FINANCE, 1, "100", today.minusDays(5));

        final PortfolioDto portfolio = portfolio(ALICE, null);
        final PortfolioGroupDto gone = portfolio.getGroups().stream()
                .filter(group -> group.getTicker().equals("GONE")).findFirst().orElseThrow();

        assertThat(gone.getIncome(), nullValue());
        // итог всего портфеля без неё - неполный
        assertThat(portfolio.getIncome().isIncomplete(), equalTo(true));
        assertThat(portfolio.getIncome().getXirr(), nullValue());
        assertThat(trades(ALICE).stream().filter(trade -> trade.getTicker().equals("GONE")).findFirst()
                .orElseThrow().getAmount(), nullValue());
    }

    private Long buy(final String userId, final String ticker, final int quantity, final String price,
                     final LocalDate date) {
        final HttpResponse<String> response = send("POST", "/positions", userId, purchase(ticker, quantity, price, date));
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        return jsonMapper.readValue(response.body(), PortfolioPositionDto.class).getId();
    }

    private PortfolioTradeDto createSale(final String userId, final String ticker, final int quantity,
                                         final String price, final LocalDate date) {
        final HttpResponse<String> response = send("POST", "/sales", userId, sale(ticker, quantity, price, date));
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        return jsonMapper.readValue(response.body(), PortfolioTradeDto.class);
    }

    private static Map<String, Object> purchase(final String ticker, final int quantity, final String price,
                                                final LocalDate date) {
        final Map<String, Object> body = new HashMap<>();
        body.put("ticker", ticker);
        body.put("quantity", quantity);
        body.put("buyPrice", new BigDecimal(price));
        body.put("buyDate", date.toString());
        return body;
    }

    private static Map<String, Object> sale(final String ticker, final int quantity, final String price,
                                            final LocalDate date) {
        final Map<String, Object> body = new HashMap<>();
        if (ticker != null) {
            body.put("ticker", ticker);
        }
        body.put("quantity", quantity);
        body.put("sellPrice", new BigDecimal(price));
        body.put("sellDate", date.toString());
        return body;
    }

    private void coupon(final String ticker, final String amount, final LocalDate date) {
        jdbcTemplate.update("insert into coupon_data (coupon_id, ticker, uid, price, currency, fix_date, payment_date,"
                        + " created_at, created_by) select c.id, b.ticker, b.uid, ?, 'rub', ?, ?, now(), 'TEST'"
                        + " from bond b join coupon c on c.bond_id = b.id where b.ticker = ?",
                new BigDecimal(amount), date.minusDays(1).atStartOfDay(), date.atStartOfDay(), ticker);
    }

    private PortfolioDto portfolio(final String userId, final String taxRate) {
        final HttpResponse<String> response = send("GET", taxRate == null ? "" : "?taxRatePercent=" + taxRate,
                userId, null);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), PortfolioDto.class);
    }

    private List<PortfolioTradeDto> trades(final String userId) {
        final HttpResponse<String> response = send("GET", "/trades", userId, null);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    private PortfolioCalendarDto calendar(final String userId) {
        final HttpResponse<String> response = send("GET", "/calendar", userId, null);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), PortfolioCalendarDto.class);
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
