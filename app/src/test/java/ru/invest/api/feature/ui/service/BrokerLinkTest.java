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
    private static final String CLICKS_URL = "http://localhost:8099/internal/rest/maintenance/broker-clicks?from=%s&to=%s";

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

    private Long clicks(final String partnerId, final String ticker) {
        return jdbcTemplate.queryForObject("select clicks from broker_click where partner_id = ? and ticker = ?",
                Long.class, partnerId, ticker);
    }
}
