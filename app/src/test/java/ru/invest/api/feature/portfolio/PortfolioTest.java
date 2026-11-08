package ru.invest.api.feature.portfolio;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.dto.portfolio.BrokerDto;
import ru.invest.api.dto.portfolio.PortfolioDto;
import ru.invest.api.dto.portfolio.PortfolioPositionDto;
import ru.invest.api.dto.portfolio.PortfolioTotalDto;
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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@Sql(scripts = "/sql/bonds-for-controller.sql", statements = "truncate table portfolio_position, portfolio_broker restart identity")
public class PortfolioTest extends AbstractInvestApplicationTest {
    private static final String BASE_URL = "http://localhost:8099/internal/rest/portfolio";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ALICE = "alice-sub";
    private static final String BOB = "bob-sub";
    // цены из bonds-for-controller.sql: номинал 1000 ₽, цена 1005 ₽ = 100,5%
    private static final String AUTO_FINANCE = "RU000A10EQ34";
    // номинал 1000 ₽, цена 610 ₽ = 61%
    private static final String OFZ = "SU26238RMFS4";
    // номинал 1000 $, цена 980 $ = 98%
    private static final String USD_BOND = "RU000A105A95";
    // цены нет
    private static final String NO_PRICE_BOND = "RU000A10ECY6";
    private static final LocalDate BUY_DATE = LocalDate.of(2026, 1, 15);
    private static final int OK = 200;
    private static final int CREATED = 201;
    private static final int NO_CONTENT = 204;
    private static final int BAD_REQUEST = 400;
    private static final int UNAUTHORIZED = 401;
    private static final int NOT_FOUND = 404;
    private static final int UNPROCESSABLE = 422;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void requestsWithoutUserAreRejected() {
        assertThat(send("GET", "", null, null).statusCode(), equalTo(UNAUTHORIZED));
        assertThat(send("POST", "/positions", null, body(AUTO_FINANCE, 1, "100")).statusCode(),
                equalTo(UNAUTHORIZED));
    }

    @Test
    void profitablePosition() {
        final PortfolioPositionDto created = create(ALICE, AUTO_FINANCE, 10, "98.5");

        assertThat(created.getId(), notNullValue());
        assertThat(created.getBondName(), equalTo("Авто Финанс Банк БО-001Р-18"));
        assertThat(created.getCurrency(), equalTo("rub"));
        assertThat(created.getBuyDate(), equalTo(BUY_DATE));
        assertThat(created.getNominal(), comparesEqualTo(new BigDecimal("1000")));
        assertThat(created.getCurrentPrice(), comparesEqualTo(new BigDecimal("100.5")));
        // 1000 × 98,5% × 10 и 1000 × 100,5% × 10
        assertThat(created.getBuyAmount(), comparesEqualTo(new BigDecimal("9850")));
        assertThat(created.getCurrentAmount(), comparesEqualTo(new BigDecimal("10050")));
        assertThat(created.getRevenue(), comparesEqualTo(new BigDecimal("200")));
        // (100,5 − 98,5) / 98,5 = 2,03%
        assertThat(created.getRevenuePercent(), comparesEqualTo(new BigDecimal("2.03")));
    }

    @Test
    void losingPositionHasNegativeRevenue() {
        final PortfolioPositionDto created = create(ALICE, OFZ, 2, "70");

        // 1000 × 70% × 2 = 1400, сейчас 1000 × 61% × 2 = 1220
        assertThat(created.getRevenue(), comparesEqualTo(new BigDecimal("-180")));
        // (61 − 70) / 70 = −12,86%
        assertThat(created.getRevenuePercent(), comparesEqualTo(new BigDecimal("-12.86")));
    }

    @Test
    void portfolioIsSortedAndTotaledByCurrency() {
        create(ALICE, OFZ, 2, "70");
        create(ALICE, AUTO_FINANCE, 10, "98.5");
        create(ALICE, USD_BOND, 1, "95");
        create(ALICE, NO_PRICE_BOND, 3, "100");
        create(BOB, AUTO_FINANCE, 1, "100");

        final PortfolioDto portfolio = portfolio(ALICE);

        assertThat(portfolio.getPositions().stream().map(PortfolioPositionDto::getTicker).toList(),
                contains(USD_BOND, NO_PRICE_BOND, AUTO_FINANCE, OFZ));
        final PortfolioPositionDto noPrice = portfolio.getPositions().get(1);
        assertThat(noPrice.getCurrentPrice(), nullValue());
        assertThat(noPrice.getRevenue(), nullValue());
        assertThat(noPrice.getRevenuePercent(), nullValue());

        // позиция без цены в итог не входит; доллары - отдельным итогом
        final List<PortfolioTotalDto> totals = portfolio.getTotals();
        assertThat(totals, hasSize(2));
        final PortfolioTotalDto rub = totals.stream().filter(total -> "rub".equals(total.getCurrency())).findFirst()
                .orElseThrow();
        assertThat(rub.getBuyAmount(), comparesEqualTo(new BigDecimal("11250")));
        assertThat(rub.getCurrentAmount(), comparesEqualTo(new BigDecimal("11270")));
        assertThat(rub.getRevenue(), comparesEqualTo(new BigDecimal("20")));
        // 20 / 11250 = 0,18%
        assertThat(rub.getRevenuePercent(), comparesEqualTo(new BigDecimal("0.18")));
        final PortfolioTotalDto usd = totals.stream().filter(total -> "usd".equals(total.getCurrency())).findFirst()
                .orElseThrow();
        assertThat(usd.getRevenue(), comparesEqualTo(new BigDecimal("30")));

        assertThat(portfolio(BOB).getPositions(), hasSize(1));
    }

    @Test
    void sameBondCanBeBoughtSeveralTimes() {
        create(ALICE, AUTO_FINANCE, 1, "99");
        create(ALICE, AUTO_FINANCE, 2, "101");

        assertThat(portfolio(ALICE).getPositions(), hasSize(2));
    }

    @Test
    void buyDateDefaultsToToday() {
        final Map<String, Object> request = body(AUTO_FINANCE, 1, "100");
        request.remove("buyDate");

        final HttpResponse<String> response = send("POST", "/positions", ALICE, request);

        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        assertThat(read(response.body(), PortfolioPositionDto.class).getBuyDate(), notNullValue());
    }

    @Test
    void invalidPositionsAreRejected() {
        assertThat(post(ALICE, body(AUTO_FINANCE, 0, "100")).statusCode(), equalTo(BAD_REQUEST));
        assertThat(post(ALICE, body(AUTO_FINANCE, 1, "0")).statusCode(), equalTo(BAD_REQUEST));
        assertThat(post(ALICE, body(AUTO_FINANCE, 1, "301")).statusCode(), equalTo(BAD_REQUEST));
        assertThat(post(ALICE, body("NO_SUCH_BOND", 1, "100")).statusCode(), equalTo(NOT_FOUND));
        assertThat(post(ALICE, body(null, 1, "100")).statusCode(), equalTo(UNPROCESSABLE));

        final Map<String, Object> future = body(AUTO_FINANCE, 1, "100");
        future.put("buyDate", LocalDate.now().plusDays(2).toString());
        assertThat(post(ALICE, future).statusCode(), equalTo(UNPROCESSABLE));

        assertThat(portfolio(ALICE).getPositions(), empty());
    }

    @Test
    void updateAndDeleteOnlyOwnPositions() {
        final PortfolioPositionDto position = create(ALICE, AUTO_FINANCE, 10, "98.5");
        final String path = "/positions/" + position.getId();

        assertThat(send("PUT", path, BOB, body(null, 5, "100")).statusCode(), equalTo(NOT_FOUND));
        assertThat(send("DELETE", path, BOB, null).statusCode(), equalTo(NOT_FOUND));

        // тикер при изменении не меняется
        final HttpResponse<String> updated = send("PUT", path, ALICE, body(OFZ, 5, "100.5"));
        assertThat(updated.body(), updated.statusCode(), equalTo(OK));
        final PortfolioPositionDto changed = read(updated.body(), PortfolioPositionDto.class);
        assertThat(changed.getTicker(), equalTo(AUTO_FINANCE));
        assertThat(changed.getQuantity(), equalTo(5));
        assertThat(changed.getRevenue(), comparesEqualTo(BigDecimal.ZERO));

        assertThat(send("DELETE", path, ALICE, null).statusCode(), equalTo(NO_CONTENT));
        assertThat(portfolio(ALICE).getPositions(), empty());
        assertThat(portfolio(ALICE).getTotals(), empty());
    }

    @Test
    void brokerIsCreatedOnceAndOfferedForNextPurchases() {
        final Map<String, Object> first = body(AUTO_FINANCE, 1, "99");
        first.put("broker", "Т-Инвестиции");
        assertThat(read(post(ALICE, first).body(), PortfolioPositionDto.class).getBroker(), equalTo("Т-Инвестиции"));

        // то же имя в другом регистре и с пробелами - тот же брокер, не новый
        final Map<String, Object> second = body(OFZ, 1, "60");
        second.put("broker", "  т-инвестиции ");
        assertThat(read(post(ALICE, second).body(), PortfolioPositionDto.class).getBroker(), equalTo("Т-Инвестиции"));

        final Map<String, Object> third = body(OFZ, 1, "60");
        third.put("broker", "Альфа");
        post(ALICE, third);
        // без брокера
        create(ALICE, AUTO_FINANCE, 1, "100");

        assertThat(brokers(ALICE).stream().map(BrokerDto::getName).toList(), contains("Альфа", "Т-Инвестиции"));
        assertThat(brokers(BOB), empty());
        assertThat(portfolio(ALICE).getPositions().stream().map(PortfolioPositionDto::getBroker).toList(),
                contains("Т-Инвестиции", null, "Т-Инвестиции", "Альфа"));
    }

    @Test
    void brokerStaysAfterItsPurchasesAreGone() {
        final Map<String, Object> request = body(AUTO_FINANCE, 1, "99");
        request.put("broker", "Сбер");
        final PortfolioPositionDto position = read(post(ALICE, request).body(), PortfolioPositionDto.class);

        // покупку перевели на другого брокера, потом удалили - «Сбер» по-прежнему в списке
        final Map<String, Object> change = body(null, 1, "99");
        change.put("broker", "ВТБ");
        final HttpResponse<String> updated = send("PUT", "/positions/" + position.getId(), ALICE, change);
        assertThat(read(updated.body(), PortfolioPositionDto.class).getBroker(), equalTo("ВТБ"));
        final Map<String, Object> cleared = body(null, 1, "99");
        cleared.put("broker", "");
        assertThat(read(send("PUT", "/positions/" + position.getId(), ALICE, cleared).body(),
                PortfolioPositionDto.class).getBroker(), nullValue());
        send("DELETE", "/positions/" + position.getId(), ALICE, null);

        assertThat(brokers(ALICE).stream().map(BrokerDto::getName).toList(), contains("ВТБ", "Сбер"));
        assertThat(send("GET", "/brokers", null, null).statusCode(), equalTo(UNAUTHORIZED));
    }

    @Test
    void tooLongBrokerIsRejected() {
        final Map<String, Object> request = body(AUTO_FINANCE, 1, "99");
        request.put("broker", "x".repeat(101));

        assertThat(post(ALICE, request).statusCode(), equalTo(BAD_REQUEST));
        assertThat(brokers(ALICE), empty());
    }

    private List<BrokerDto> brokers(final String userId) {
        final HttpResponse<String> response = send("GET", "/brokers", userId, null);
        assertThat(response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    private PortfolioPositionDto create(final String userId, final String ticker, final int quantity,
                                        final String buyPrice) {
        final HttpResponse<String> response = post(userId, body(ticker, quantity, buyPrice));
        assertThat(response.body(), response.statusCode(), equalTo(CREATED));
        return read(response.body(), PortfolioPositionDto.class);
    }

    private static Map<String, Object> body(final String ticker, final int quantity, final String buyPrice) {
        final Map<String, Object> body = new HashMap<>();
        if (ticker != null) {
            body.put("ticker", ticker);
        }
        body.put("quantity", quantity);
        body.put("buyPrice", new BigDecimal(buyPrice));
        body.put("buyDate", BUY_DATE.toString());
        return body;
    }

    private HttpResponse<String> post(final String userId, final Map<String, Object> body) {
        return send("POST", "/positions", userId, body);
    }

    private PortfolioDto portfolio(final String userId) {
        final HttpResponse<String> response = send("GET", "", userId, null);
        assertThat(response.statusCode(), equalTo(OK));
        return read(response.body(), PortfolioDto.class);
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
