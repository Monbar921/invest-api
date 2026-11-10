package ru.invest.api.feature.ui.service;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.bond.collection.BondCollectionDto;
import ru.invest.api.dto.bond.collection.BondCollectionPageDto;
import ru.invest.api.dto.bond.issuer.IssuerDto;
import ru.invest.api.dto.bond.news.NewsDto;
import ru.invest.api.dto.bond.sitemap.SitemapDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.dto.request.bond.BondParametersRequest;
import ru.invest.api.dto.request.bond.DateRangeRequest;
import ru.invest.api.starter.client.InvestApiBondClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Данные для SEO-страниц: эмитент, подборки, sitemap и фильтр по дате погашения.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql")
class SeoEndpointsTest extends AbstractInvestApplicationTest {
    // ОФЗ, rub, ISIN RU000A1038V6, погашение 2041-05-15
    private static final String OFZ = "SU26238RMFS4";
    // rub, переменный купон, погашение 2036-01-18
    private static final String FLOATER = "RU000A10EHC1";
    // rub, погашение 2029-03-08
    private static final String RUB_MODERATE = "RU000A1080Y2";
    // rub, погашение 2029-03-14
    private static final String RUB_LOW = "RU000A10EQ34";
    // cny, погашение 2026-12-23
    private static final String CNY_MODERATE = "RU000A106Z77";
    private static final long ISSUER_ID = 1001L;
    private static final long OTHER_ISSUER_ID = 1002L;
    private static final int NOT_FOUND = 404;

    @Autowired
    private InvestApiBondClient investApiBondClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DateTimeMapper dateTimeMapper;
    @Autowired
    private List<CacheManager> cacheManagers;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("truncate table news restart identity cascade");
        jdbcTemplate.execute("truncate table bond_issuer restart identity");
        // два выпуска одного эмитента и выпуск другого, у которого выпусков в системе нет
        insertIssuer(RUB_LOW, ISSUER_ID, "Публичное акционерное общество \"Авто Финанс Банк\"", "7716642273");
        insertIssuer(RUB_MODERATE, ISSUER_ID, null, null);
        insertIssuer("RU000NOTINDB", OTHER_ISSUER_ID, "Акционерное общество \"Нет в системе\"", "1");
        clearCaches();
    }

    @Test
    void issuerHasTitleInnAndBondsByMaturity() {
        final IssuerDto issuer = investApiBondClient.getIssuer(ISSUER_ID);

        assertThat(issuer.getId(), equalTo(ISSUER_ID));
        // название и ИНН есть только у одного выпуска - берутся оттуда
        assertThat(issuer.getTitle(), equalTo("Публичное акционерное общество \"Авто Финанс Банк\""));
        assertThat(issuer.getShortTitle(), equalTo("ПАО \"Авто Финанс Банк\""));
        assertThat(issuer.getInn(), equalTo("7716642273"));
        assertThat(tickers(issuer.getBonds()), contains(RUB_MODERATE, RUB_LOW));
        assertThat(issuer.getBonds().getFirst().getPrice(), notNullValue());
    }

    @Test
    void issuerWithoutBondsInSystemHasEmptyList() {
        assertThat(investApiBondClient.getIssuer(OTHER_ISSUER_ID).getBonds(), empty());
    }

    @Test
    void unknownIssuerIsNotFound() {
        final FeignException exception = assertThrows(FeignException.class, () -> investApiBondClient.getIssuer(999L));
        assertThat(exception.status(), equalTo(NOT_FOUND));
    }

    @Test
    void issuerNewsByIssuerLinkAndByIsin() {
        final Long byIssuer = insertNews("Рейтинг эмитента", LocalDateTime.of(2026, 11, 1, 10, 0));
        jdbcTemplate.update("insert into news_link (news_id, emitter_id) values (?, ?)", byIssuer, ISSUER_ID);
        final Long byIsin = insertNews("Купон выпуска", LocalDateTime.of(2026, 11, 2, 10, 0));
        jdbcTemplate.update("insert into news_link (news_id, isin) values (?, ?)", byIsin, RUB_MODERATE);
        final Long other = insertNews("Другая новость", LocalDateTime.of(2026, 11, 3, 10, 0));
        jdbcTemplate.update("insert into news_link (news_id, isin) values (?, ?)", other, OFZ);

        final PageDto<NewsDto> news = investApiBondClient.getIssuerNews(ISSUER_ID, null, null);

        assertThat(news.getContent().stream().map(NewsDto::getTitle).toList(), contains("Купон выпуска", "Рейтинг эмитента"));
        assertThat(investApiBondClient.getIssuerNews(999L, null, null).getContent(), empty());
    }

    @Test
    void bondCardHasIssuer() {
        final BondDto bond = investApiBondClient.getByTicker(RUB_LOW);

        assertThat(bond.getIssuer().getId(), equalTo(ISSUER_ID));
        assertThat(bond.getIssuer().getShortTitle(), equalTo("ПАО \"Авто Финанс Банк\""));
        assertThat(investApiBondClient.getByTicker(OFZ).getIssuer(), nullValue());
        // в списке эмитента нет - он нужен только карточке
        assertThat(investApiBondClient.getAllPage(null, null, new BondParametersRequest()).getContent().stream()
                .map(BondDto::getIssuer).toList(), everyItem(nullValue()));
    }

    @Test
    void maturityDateFilterIncludesBoundsAndDropsBondsWithoutDate() {
        jdbcTemplate.update("update bond set maturity_date = null where ticker = ?", OFZ);
        clearCaches();
        final BondParametersRequest request = new BondParametersRequest();
        request.setMaturityDate(new DateRangeRequest().setFrom(LocalDate.of(2029, 3, 8)).setTo(LocalDate.of(2029, 3, 14)));

        assertThat(tickers(investApiBondClient.getAllPage(null, null, request).getContent()),
                containsInAnyOrder(RUB_MODERATE, RUB_LOW));

        request.setMaturityDate(new DateRangeRequest().setFrom(LocalDate.of(2030, 1, 1)));
        // ОФЗ без даты погашения не проходит
        assertThat(tickers(investApiBondClient.getAllPage(null, null, request).getContent()),
                containsInAnyOrder(FLOATER, "RU000A10ECY6"));
    }

    @Test
    void collectionsList() {
        assertThat(investApiBondClient.getCollections().stream().map(BondCollectionDto::getSlug).toList(),
                contains("ofz-top-yield", "floaters", "maturity-this-year"));
    }

    @Test
    void ofzCollectionHasOnlyOfz() {
        final BondCollectionPageDto collection = investApiBondClient.getCollection("ofz-top-yield", null, null);

        assertThat(collection.getTitle(), equalTo("Самые доходные ОФЗ"));
        assertThat(collection.getDescription(), notNullValue());
        assertThat(tickers(collection.getBonds().getContent()), contains(OFZ));
    }

    @Test
    void floatersCollectionHasOnlyFloatingCoupon() {
        assertThat(tickers(investApiBondClient.getCollection("floaters", null, null).getBonds().getContent()),
                contains(FLOATER));
    }

    @Test
    void maturityThisYearFromTodayToEndOfYear() {
        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        final LocalDate endOfYear = LocalDate.of(today.getYear(), 12, 31);
        jdbcTemplate.update("update bond set maturity_date = ? where ticker = ?", endOfYear.atStartOfDay(), RUB_LOW);
        jdbcTemplate.update("update bond set maturity_date = ? where ticker = ?", today.atStartOfDay(), RUB_MODERATE);
        jdbcTemplate.update("update bond set maturity_date = ? where ticker = ?", today.minusDays(1).atStartOfDay(), OFZ);
        jdbcTemplate.update("update bond set maturity_date = ? where ticker = ?", endOfYear.plusDays(1).atStartOfDay(), FLOATER);
        jdbcTemplate.update("update bond set maturity_date = ? where ticker = ?", endOfYear.plusYears(1).atStartOfDay(), CNY_MODERATE);
        clearCaches();

        final BondCollectionPageDto collection = investApiBondClient.getCollection("maturity-this-year", null, null);

        assertThat(tickers(collection.getBonds().getContent()), contains(RUB_MODERATE, RUB_LOW));
    }

    @Test
    void unknownCollectionIsNotFound() {
        final FeignException exception = assertThrows(FeignException.class,
                () -> investApiBondClient.getCollection("unknown", null, null));
        assertThat(exception.status(), equalTo(NOT_FOUND));
    }

    @Test
    void sitemapListsBondsIssuersWithBondsAndCollections() {
        final SitemapDto sitemap = investApiBondClient.getSitemap();

        assertThat(sitemap.getTickers(), hasSize(7));
        assertThat(sitemap.getTickers().getFirst(), equalTo("RU000A105A95"));
        // эмитент без выпусков в системе в sitemap не попадает
        assertThat(sitemap.getIssuerIds(), contains(ISSUER_ID));
        assertThat(sitemap.getCollections(), contains("ofz-top-yield", "floaters", "maturity-this-year"));
    }

    private void insertIssuer(final String isin, final long emitterId, final String title, final String inn) {
        jdbcTemplate.update("insert into bond_issuer (isin, emitter_id, title, inn, updated_at) values (?, ?, ?, ?, now())",
                isin, emitterId, title, inn);
    }

    private Long insertNews(final String title, final LocalDateTime publishedAt) {
        return jdbcTemplate.queryForObject("""
                insert into news (source, external_id, title, url, published_at, created_at)
                values ('MOEX', ?, ?, 'https://news.test', ?, now()) returning id""", Long.class,
                title + System.nanoTime(), title, publishedAt);
    }

    private void clearCaches() {
        cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames()
                .forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)));
    }

    private static List<String> tickers(final List<BondDto> bonds) {
        return bonds.stream().map(BondDto::getTicker).toList();
    }
}
