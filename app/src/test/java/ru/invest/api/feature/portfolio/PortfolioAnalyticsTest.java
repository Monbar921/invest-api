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
import ru.invest.api.dto.portfolio.PortfolioAnalyticsDto;
import ru.invest.api.dto.portfolio.PortfolioAnalyticsShareDto;
import ru.invest.api.dto.portfolio.PortfolioUnvaluedPositionDto;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Аналитика портфеля через REST: данные облигаций, эмитенты и рейтинги - из БД, как у карточки.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql", statements = "truncate table portfolio_sale, portfolio_position, "
        + "portfolio_broker, bond_issuer, credit_rating, currency_rate restart identity")
public class PortfolioAnalyticsTest extends AbstractInvestApplicationTest {
    private static final String ANALYTICS_URL = "http://localhost:8099/internal/rest/portfolio/analytics";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String ALICE = "alice-sub";
    private static final String BOB = "bob-sub";
    // bonds-for-controller.sql: ОФЗ 26238, government, цена 61 % от 1000 ₽, погашение 2041
    private static final String OFZ = "SU26238RMFS4";
    private static final String OFZ_ISIN = "RU000A1038V6";
    // Авто Финанс Банк, financial, 100,5 % от 1000 ₽, постоянный купон, погашение 2029
    private static final String BANK = "RU000A10EQ34";
    // флоатер, consumer, 95,05 % от 1000 ₽, погашение 2036
    private static final String FLOATER = "RU000A10EHC1";
    // цены нет
    private static final String NO_PRICE = "RU000A10ECY6";
    private static final String UNKNOWN = "XX000UNKNOWN";
    private static final int OK = 200;
    private static final int UNAUTHORIZED = 401;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final LocalDate today = LocalDate.now(ZoneOffset.UTC);

    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private List<CacheManager> cacheManagers;

    @BeforeEach
    void setUp() {
        cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames()
                .forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)));
    }

    @Test
    void requestWithoutUserIsRejected() {
        assertThat(send(null).statusCode(), equalTo(UNAUTHORIZED));
    }

    @Test
    void emptyPortfolio() {
        final PortfolioAnalyticsDto analytics = analytics(ALICE);
        assertThat(analytics.getValueInRub(), nullValue());
        assertThat(analytics.getBonds(), equalTo(0));
        assertThat(analytics.getIssuers(), empty());
        assertThat(analytics.getRateSensitivity(), nullValue());
    }

    @Test
    void structureAndRisk() {
        position(ALICE, OFZ, 2);
        position(ALICE, BANK, 1);
        position(ALICE, FLOATER, 1);
        position(ALICE, NO_PRICE, 3);
        position(ALICE, UNKNOWN, 1);
        position(BOB, OFZ, 100);
        issuer(OFZ_ISIN, 100L, "Министерство финансов Российской Федерации");
        issuer(BANK, 200L, "Публичное акционерное общество \"Авто Финанс Банк\"");
        jdbcTemplate.update("""
                insert into credit_rating (agency, scope, emitter_id, rating, grade, score, withdrawn, action_date)
                values ('ACRA', 'ISSUER', 200, 'A+(RU)', 'A+', 16, false, ?)""", today.minusDays(30));
        metrics(OFZ, "14.5", "8", "7");
        metrics(BANK, "20", "2", "1.8");
        // у флоатера дюрация есть, но в чувствительность к ставке он не входит
        metrics(FLOATER, null, null, "0.25");

        final PortfolioAnalyticsDto analytics = analytics(ALICE);

        // 2 × 610 + 1005 + 950,50; без цены и неизвестная облигация - отдельно
        assertThat(analytics.getValueInRub(), comparesEqualTo(new BigDecimal("3175.50")));
        assertThat(analytics.getBonds(), equalTo(5));
        assertThat(analytics.getUnvalued().stream().map(PortfolioUnvaluedPositionDto::getTicker).toList(),
                contains(NO_PRICE, UNKNOWN));
        assertThat(analytics.getUnvalued().stream().map(PortfolioUnvaluedPositionDto::getReason).toList(),
                contains("NO_PRICE", "UNKNOWN_BOND"));

        // у флоатера эмитент не известен - «нет данных», а не пропал
        assertThat(keys(analytics.getIssuers()), contains("100", "200", null));
        assertThat(analytics.getIssuers().get(1).getTitle(), equalTo("ПАО \"Авто Финанс Банк\""));
        assertThat(shares(analytics.getIssuers()), contains("38.42", "31.65", "29.93"));
        assertThat(analytics.getLargestIssuer().getKey(), equalTo("100"));
        assertThat(analytics.getLargestIssuer().getShare(), comparesEqualTo(new BigDecimal("38.42")));

        assertThat(keys(analytics.getSectors()), contains("government", "financial", "consumer"));
        assertThat(keys(analytics.getRatings()), contains("A+", null));
        assertThat(shares(analytics.getRatings()), contains("31.65", "68.35"));
        assertThat(keys(analytics.getCurrencies()), contains("rub"));
        assertThat(keys(analytics.getCouponTypes()), contains("FIXED", "FLOATING"));
        assertThat(analytics.getCouponTypes().getFirst().getTickers(), contains(OFZ, BANK));
        assertThat(keys(analytics.getMaturities()), contains("2029", "2036", "2041"));
        for (final List<PortfolioAnalyticsShareDto> breakdown : List.of(analytics.getIssuers(),
                analytics.getSectors(), analytics.getRatings(), analytics.getCurrencies(),
                analytics.getCouponTypes(), analytics.getMaturities())) {
            assertThat(breakdown.stream().map(PortfolioAnalyticsShareDto::getShare)
                    .reduce(BigDecimal.ZERO, BigDecimal::add), comparesEqualTo(new BigDecimal("100")));
        }

        // (14,5 × 1220 + 20 × 1005) / 2225
        assertThat(analytics.getYieldToMaturity().getValue(), comparesEqualTo(new BigDecimal("16.98")));
        assertThat(analytics.getYieldToMaturity().getCoveredShare(), comparesEqualTo(new BigDecimal("70.07")));
        // (7 × 1220 + 1,8 × 1005 + 0,25 × 950,5) / 3175,5
        assertThat(analytics.getDurationModified().getValue(), comparesEqualTo(new BigDecimal("3.3338")));
        assertThat(analytics.getDurationModified().getCoveredShare(), comparesEqualTo(new BigDecimal("100")));
        // без флоатера: (7 × 1220 + 1,8 × 1005) × 1 % = 103,49 ₽ - 4,65 % от 2225 ₽
        assertThat(analytics.getRateSensitivity().getValueChangeUp(), comparesEqualTo(new BigDecimal("-103.49")));
        assertThat(analytics.getRateSensitivity().getValueChangeDown(), comparesEqualTo(new BigDecimal("103.49")));
        assertThat(analytics.getRateSensitivity().getChangePercent(), comparesEqualTo(new BigDecimal("4.65")));
        assertThat(analytics.getRateSensitivity().getCoveredShare(), comparesEqualTo(new BigDecimal("70.07")));
    }

    @Test
    void soldPositionsAreNotCounted() {
        position(ALICE, OFZ, 2);
        position(ALICE, BANK, 1);
        jdbcTemplate.update("insert into portfolio_sale (user_id, ticker, quantity, sell_price, sell_date, created_at)"
                + " values (?, ?, 1, 100, ?, now())", ALICE, BANK, today.minusDays(1));

        final PortfolioAnalyticsDto analytics = analytics(ALICE);

        assertThat(analytics.getValueInRub(), comparesEqualTo(new BigDecimal("1220")));
        assertThat(keys(analytics.getSectors()), contains("government"));
        assertThat(shares(analytics.getSectors()), contains("100.00"));
    }

    private void position(final String userId, final String ticker, final int quantity) {
        jdbcTemplate.update("insert into portfolio_position (user_id, ticker, quantity, buy_price, buy_date, created_at)"
                + " values (?, ?, ?, 100, ?, now())", userId, ticker, quantity, today.minusDays(5));
    }

    private void issuer(final String isin, final long emitterId, final String title) {
        jdbcTemplate.update("insert into bond_issuer (isin, emitter_id, title, updated_at) values (?, ?, ?, now())",
                isin, emitterId, title);
    }

    private void metrics(final String ticker, final String yield, final String macaulay, final String modified) {
        jdbcTemplate.update("update coupon set yield_to_maturity = ?, duration_macaulay = ?, duration_modified = ?"
                + " where ticker = ?", number(yield), number(macaulay), number(modified), ticker);
    }

    private static BigDecimal number(final String text) {
        return text == null ? null : new BigDecimal(text);
    }

    private PortfolioAnalyticsDto analytics(final String userId) {
        final HttpResponse<String> response = send(userId);
        assertThat(response.body(), response.statusCode(), equalTo(OK));
        return jsonMapper.readValue(response.body(), PortfolioAnalyticsDto.class);
    }

    private static List<String> keys(final List<PortfolioAnalyticsShareDto> shares) {
        return shares.stream().map(PortfolioAnalyticsShareDto::getKey).toList();
    }

    private static List<String> shares(final List<PortfolioAnalyticsShareDto> shares) {
        return shares.stream().map(share -> share.getShare().toPlainString()).toList();
    }

    @SneakyThrows
    private HttpResponse<String> send(final String userId) {
        final HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(ANALYTICS_URL)).GET();
        if (userId != null) {
            request.header(USER_ID_HEADER, userId);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
