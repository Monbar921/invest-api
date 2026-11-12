package ru.invest.api.feature.ui.service;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.broker.service.config.BrokerReferralProperties;
import ru.invest.api.broker.service.config.BrokerReferralProperties.Partner;
import ru.invest.api.dto.bond.broker.BrokerLinkDto;
import ru.invest.api.starter.client.InvestApiBondClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Блок «Купить у брокера»: ссылки партнёров из конфигурации (тестовая - в application.yml) и учёт переходов.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql")
class BrokerLinkTest extends AbstractInvestApplicationTest {
    // ОФЗ, ISIN RU000A1038V6
    private static final String OFZ = "SU26238RMFS4";
    private static final String OFZ_ISIN = "RU000A1038V6";
    private static final String OTHER = "RU000A10EQ34";
    private static final int NOT_FOUND = 404;
    private static final int BAD_REQUEST = 400;
    private static final int PARALLEL_CLICKS = 20;
    private static final String CLICKS_BASE_URL = "http://localhost:8099/internal/rest/maintenance/broker-clicks";
    private static final String CLICKS_URL = CLICKS_BASE_URL + "?from=%s&to=%s";

    @Autowired
    private InvestApiBondClient investApiBondClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private BrokerReferralProperties properties;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("truncate table broker_click restart identity");
    }

    @Test
    void linksOfEnabledPartnersByTemplateInConfigOrder() {
        final List<BrokerLinkDto> links = investApiBondClient.getBrokerLinks(OFZ);

        assertThat(links.stream().map(BrokerLinkDto::getPartnerId).toList(), contains("alpha", "beta"));
        final BrokerLinkDto alpha = links.getFirst();
        assertThat(alpha.getName(), equalTo("Альфа Брокер"));
        assertThat(alpha.getUrl(), equalTo("https://alpha.test/bonds/" + OFZ_ISIN + "?ref=invest&erid=2SDnjAlpha"));
        assertThat(alpha.getAdvertiser(), equalTo("АО \"Альфа Брокер\", ИНН 7700000001"));
        assertThat(alpha.getErid(), equalTo("2SDnjAlpha"));
        final BrokerLinkDto beta = links.get(1);
        assertThat(beta.getUrl(), equalTo("https://beta.test/buy?ticker=" + OFZ));
        assertThat(beta.getErid(), nullValue());
    }

    @Test
    void noPartnersNoLinks() {
        final List<Partner> partners = properties.getPartners();
        properties.setPartners(new ArrayList<>());
        try {
            assertThat(investApiBondClient.getBrokerLinks(OFZ), empty());
            // без партнёров облигация не ищется: блок просто не показывается
            assertThat(investApiBondClient.getBrokerLinks("UNKNOWN"), empty());
        } finally {
            properties.setPartners(partners);
        }
    }

    @Test
    void unknownBondLinksNotFound() {
        final FeignException exception = assertThrows(FeignException.class,
                () -> investApiBondClient.getBrokerLinks("UNKNOWN"));
        assertThat(exception.status(), equalTo(NOT_FOUND));
    }

    @Test
    void clicksAreCountedPerDayPartnerAndBond() throws Exception {
        investApiBondClient.registerBrokerClick(OFZ, "alpha");
        investApiBondClient.registerBrokerClick(OFZ, "alpha");
        investApiBondClient.registerBrokerClick(OFZ, "beta");
        investApiBondClient.registerBrokerClick(OTHER, "alpha");

        final LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        assertThat(clicks("alpha", OFZ), equalTo(2L));
        assertThat(clicks("beta", OFZ), equalTo(1L));
        assertThat(clicks("alpha", OTHER), equalTo(1L));
        assertThat(jdbcTemplate.queryForObject("select distinct click_date from broker_click", LocalDate.class),
                equalTo(today));

        final HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(CLICKS_URL.formatted(today, today))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode(), equalTo(200));
        assertThat(response.body(), containsString(
                "{\"clicks\":2,\"date\":\"" + today + "\",\"partnerId\":\"alpha\",\"ticker\":\"" + OFZ + "\"}"));
    }

    @Test
    void clickOnDisabledOrUnknownPartnerOrBondIsNotFound() {
        assertThat(assertThrows(FeignException.class, () -> investApiBondClient.registerBrokerClick(OFZ, "gamma"))
                .status(), equalTo(NOT_FOUND));
        assertThat(assertThrows(FeignException.class, () -> investApiBondClient.registerBrokerClick(OFZ, "nobody"))
                .status(), equalTo(NOT_FOUND));
        assertThat(assertThrows(FeignException.class, () -> investApiBondClient.registerBrokerClick("UNKNOWN", "alpha"))
                .status(), equalTo(NOT_FOUND));
        assertThat(jdbcTemplate.queryForObject("select count(*) from broker_click", Long.class), equalTo(0L));
    }

    @Test
    void parallelClicksAreNotLost() throws Exception {
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_CLICKS)) {
            for (int i = 0; i < PARALLEL_CLICKS; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    investApiBondClient.registerBrokerClick(OFZ, "alpha");
                    return null;
                }));
            }
            start.countDown();
            for (final Future<?> result : results) {
                // клик упал - тест падает с его ошибкой
                result.get();
            }
        }

        assertThat(clicks("alpha", OFZ), equalTo((long) PARALLEL_CLICKS));
        assertThat(jdbcTemplate.queryForObject("select count(*) from broker_click", Long.class), equalTo(1L));
    }

    @Test
    void maintenanceClicksIncludeBothBoundariesInOrder() throws Exception {
        jdbcTemplate.update("insert into broker_click (click_date, partner_id, ticker, clicks) values "
                + "('2026-11-09', 'alpha', ?, 1), ('2026-11-10', 'beta', ?, 2), ('2026-11-10', 'alpha', ?, 3), "
                + "('2026-11-10', 'alpha', ?, 4), ('2026-11-11', 'alpha', ?, 5), ('2026-11-12', 'alpha', ?, 6)",
                OFZ, OFZ, OTHER, OFZ, OFZ, OFZ);

        final HttpResponse<String> range = getClicks("from=2026-11-10&to=2026-11-11");
        assertThat(range.statusCode(), equalTo(200));
        // день, затем партнёр, затем тикер; 09.11 и 12.11 вне диапазона
        assertThat(range.body(), equalTo("["
                + "{\"clicks\":3,\"date\":\"2026-11-10\",\"partnerId\":\"alpha\",\"ticker\":\"" + OTHER + "\"},"
                + "{\"clicks\":4,\"date\":\"2026-11-10\",\"partnerId\":\"alpha\",\"ticker\":\"" + OFZ + "\"},"
                + "{\"clicks\":2,\"date\":\"2026-11-10\",\"partnerId\":\"beta\",\"ticker\":\"" + OFZ + "\"},"
                + "{\"clicks\":5,\"date\":\"2026-11-11\",\"partnerId\":\"alpha\",\"ticker\":\"" + OFZ + "\"}]"));

        final HttpResponse<String> oneDay = getClicks("from=2026-11-12&to=2026-11-12");
        assertThat(oneDay.body(), containsString("\"clicks\":6"));
        assertThat(getClicks("from=2026-11-12&to=2026-11-09").body(), equalTo("[]"));
        assertThat(getClicks("from=2026-11-13&to=2026-11-30").body(), equalTo("[]"));
    }

    @Test
    void maintenanceClicksWithoutOrWithInvalidDatesIsBadRequest() throws Exception {
        assertThat(getClicks("from=2026-11-10").statusCode(), equalTo(BAD_REQUEST));
        assertThat(getClicks("to=2026-11-10").statusCode(), equalTo(BAD_REQUEST));
        assertThat(getClicks("from=2026-13-01&to=2026-12-31").statusCode(), equalTo(BAD_REQUEST));
        assertThat(getClicks("from=10.11.2026&to=2026-11-11").statusCode(), equalTo(BAD_REQUEST));
        assertThat(getClicks("from=&to=2026-11-11").statusCode(), equalTo(BAD_REQUEST));
    }

    private static HttpResponse<String> getClicks(final String query) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(CLICKS_BASE_URL + "?" + query)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private Long clicks(final String partnerId, final String ticker) {
        return jdbcTemplate.queryForObject("select clicks from broker_click where partner_id = ? and ticker = ?",
                Long.class, partnerId, ticker);
    }
}
