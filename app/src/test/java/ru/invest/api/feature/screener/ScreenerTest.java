package ru.invest.api.feature.screener;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.bond.screener.CreditRatingDto;
import ru.invest.api.dto.bond.screener.RatingAgencyDto;
import ru.invest.api.dto.bond.screener.RatingOutlookDto;
import ru.invest.api.dto.bond.screener.RatingScopeDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.dto.request.bond.BondParametersRequest;
import ru.invest.api.dto.request.bond.BondSortFieldRequest;
import ru.invest.api.dto.request.bond.BondSortOrderRequest;
import ru.invest.api.dto.request.bond.BondSortRequest;
import ru.invest.api.dto.request.bond.RatingRangeRequest;
import ru.invest.api.dto.request.bond.ValueRangeRequest;
import ru.invest.api.news.supplier.client.feign.MoexIssClient;
import ru.invest.api.news.supplier.client.http.NewsHttpClient;
import ru.invest.api.news.supplier.model.IssTableDto;
import ru.invest.api.news.supplier.usecase.CreditRatingSyncUseCase;
import ru.invest.api.news.supplier.usecase.ExternalNewsUseCase;
import ru.invest.api.news.supplier.usecase.NewsSyncUseCase;
import ru.invest.api.starter.client.InvestApiBondClient;
import ru.invest.api.tinkoff.supplier.usecase.CouponInterestUseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

/**
 * Данные скринера через REST: рейтинги из релизов агентств, дюрация, ликвидность по свечам, спред к ОФЗ, база
 * флоатера, новые фильтры и сортировки, выгрузка. Облигации - bonds-for-controller.sql, остальное вставляется SQL.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql")
class ScreenerTest extends AbstractInvestApplicationTest {
    // ОФЗ, rub, government, 2 купона в год, погашение 2041-05-15
    private static final String OFZ = "SU26238RMFS4";
    // rub, consumer, без купона в БД
    private static final String RUB_MODERATE = "RU000A1080Y2";
    // rub, consumer, флоатер, 4 купона в год
    private static final String FLOATER = "RU000A10EHC1";
    // rub, financial, 12 купонов в год, «Авто Финанс Банк БО-001Р-18»
    private static final String RUB_LOW = "RU000A10EQ34";
    // usd
    private static final String USD = "RU000A105A95";
    // cny, погашение 2026-12-23
    private static final String CNY = "RU000A106Z77";
    private static final long AFB = 1001L;
    private static final long PUSHKINO = 1002L;
    private static final long MINFIN = 1003L;
    private static final String ACRA_URL = "https://acra.test/press-releases/";
    private static final String NKR_URL = "https://nkr.test/ratings/press-releases/";
    private static final String[] RU_MONTHS = {"янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"};
    private static final BigDecimal PRECISION = new BigDecimal("0.01");
    private static final int BAD_REQUEST = 400;

    @Autowired
    private InvestApiBondClient investApiBondClient;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DateTimeMapper dateTimeMapper;
    @Autowired
    private List<CacheManager> cacheManagers;
    @Autowired
    private MoexIssClient moexIssClient;
    @Autowired
    private NewsHttpClient newsHttpClient;
    @Autowired
    private ExternalNewsUseCase externalNewsUseCase;
    @Autowired
    private CreditRatingSyncUseCase creditRatingSyncUseCase;
    @Autowired
    private NewsSyncUseCase newsSyncUseCase;
    @Autowired
    private CouponInterestUseCase couponInterestUseCase;
    @Autowired
    private AuditMapper auditMapper;

    private LocalDate today;
    private AuditModel audit;

    @BeforeEach
    void setUp() {
        reset(moexIssClient, newsHttpClient);
        today = dateTimeMapper.getNow().toLocalDate();
        audit = auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS);
        jdbcTemplate.execute("truncate table news restart identity cascade");
        jdbcTemplate.execute("truncate table bond_issuer restart identity");
        insertIssuer(RUB_LOW, AFB, "Публичное акционерное общество \"Авто Финанс Банк\"");
        insertIssuer(RUB_MODERATE, PUSHKINO, "Акционерное общество \"Пушкинское ПЗ\"");
        insertIssuer(OFZ, MINFIN, "Министерство финансов Российской Федерации");

        // доходность и дюрация - как после синхронизации цен
        setYield(OFZ, "15.00", "7.5000", "6.5217");
        setYield(RUB_LOW, "20.00", "1.8000", "1.5000");
        setYield(USD, "7.00", "1.1000", "1.0280");
        jdbcTemplate.update("update bond set offer_date = ? where ticker = ?", today.plusDays(100).atStartOfDay(), RUB_LOW);
        jdbcTemplate.update("update bond set amortization_flag = true where ticker = ?", FLOATER);
        when(moexIssClient.getZeroCouponCurve(any())).thenReturn(curveResponse());
        clearCaches();
    }

    @Test
    void ratingsFromAgencyReleases() {
        mockAcra(ACRA_URL,
                acraRow("7001", "АКРА ПОДТВЕРДИЛО КРЕДИТНЫЙ РЕЙТИНГ ПАО «АВТО ФИНАНС БАНК» НА УРОВНЕ A+(RU), ПРОГНОЗ «СТАБИЛЬНЫЙ»",
                        "ПАО «Авто Финанс Банк»", today),
                acraRow("7002", "АКРА ПРИСВОИЛО ВЫПУСКУ ОБЛИГАЦИЙ АО «ПУШКИНСКОЕ ПЗ» (RU000A1080Y2) КРЕДИТНЫЙ РЕЙТИНГ BBB-(RU)",
                        "АО «Пушкинское ПЗ»", today));
        // НКР: понижение сегодня и более старое присвоение - старое не заменяет новое
        mockNkr(nkrRow("AFB-2", "НКР понизило кредитный рейтинг ПАО «Авто Финанс Банк» с A+.ru до A.ru, прогноз — негативный", today),
                nkrRow("AFB-1", "НКР присвоило ПАО «Авто Финанс Банк» кредитный рейтинг AA.ru со стабильным прогнозом",
                        today.minusDays(30)));

        creditRatingSyncUseCase.apply(externalNewsUseCase.getRatingNews(), audit);
        // рейтинг Минфина без пересмотра дольше срока действия не используется
        jdbcTemplate.update("""
                insert into credit_rating (agency, scope, emitter_id, rating, grade, score, withdrawn, action_date)
                values ('NKR', 'ISSUER', ?, 'AAA.ru', 'AAA', 20, false, ?)""", MINFIN, today.minusMonths(16));
        clearCaches();

        // по рейтингу от лучшего: у Авто Финанс Банка - самый низкий из агентств (A у НКР), без рейтинга - в конце
        final List<BondDto> byRating = page(new BondParametersRequest().setBondSorts(sort(BondSortFieldRequest.CREDIT_RATING,
                BondSortOrderRequest.DESC))).getContent();
        assertThat(tickers(byRating).subList(0, 2), contains(RUB_LOW, RUB_MODERATE));
        assertThat(byRating.get(2).getCreditRating(), nullValue());
        final CreditRatingDto lowest = byRating.getFirst().getCreditRating();
        assertThat(lowest.getAgency(), equalTo(RatingAgencyDto.NKR));
        assertThat(lowest.getRating(), equalTo("A.ru"));
        assertThat(lowest.getGrade(), equalTo("A"));
        assertThat(lowest.getScore(), equalTo(15));
        assertThat(lowest.getScope(), equalTo(RatingScopeDto.ISSUER));
        assertThat(lowest.getOutlook(), equalTo(RatingOutlookDto.NEGATIVE));
        assertThat(lowest.getActionDate(), equalTo(today));
        assertThat(byRating.get(1).getCreditRating().getScope(), equalTo(RatingScopeDto.ISSUE));
        assertThat(byRating.stream().filter(bond -> OFZ.equals(bond.getTicker())).findFirst().orElseThrow().getCreditRating(),
                nullValue());
        // в списке - без рейтингов по агентствам, в карточке - оба агентства
        assertThat(byRating.getFirst().getCreditRatings(), nullValue());
        final BondDto card = investApiBondClient.getByTicker(RUB_LOW);
        assertThat(card.getCreditRatings().stream().map(CreditRatingDto::getRating).toList(), contains("A+(RU)", "A.ru"));
        assertThat(card.getCreditRating().getRating(), equalTo("A.ru"));

        // фильтр по общей шкале: не ниже A-; от BBB- до BBB
        assertThat(tickers(page(new BondParametersRequest().setCreditRating(new RatingRangeRequest().setMin("A-"))).getContent()),
                contains(RUB_LOW));
        assertThat(tickers(page(new BondParametersRequest()
                .setCreditRating(new RatingRangeRequest().setMin("BBB-").setMax("BBB"))).getContent()), contains(RUB_MODERATE));
        final FeignException badGrade = assertThrows(FeignException.class,
                () -> page(new BondParametersRequest().setCreditRating(new RatingRangeRequest().setMin("Z"))));
        assertThat(badGrade.status(), equalTo(BAD_REQUEST));

        // АКРА отозвало рейтинг эмитента, НКР присвоило рейтинг выпуску по номеру серии (ISIN в заголовке нет)
        mockAcra(ACRA_URL, acraRow("7003", "АКРА ОТОЗВАЛО КРЕДИТНЫЙ РЕЙТИНГ ПАО «АВТО ФИНАНС БАНК» ПО ИНИЦИАТИВЕ РЕЙТИНГУЕМОГО ЛИЦА",
                "ПАО «Авто Финанс Банк»", today));
        mockNkr(nkrRow("AFB-3", "НКР присвоило выпуску биржевых облигаций ПАО «Авто Финанс Банк» серии БО-001P-18 кредитный "
                + "рейтинг AA-.ru", today));
        creditRatingSyncUseCase.apply(externalNewsUseCase.getRatingNews(), audit);
        // повторный разбор тех же релизов ничего не меняет
        creditRatingSyncUseCase.apply(externalNewsUseCase.getRatingNews(), audit);
        clearCaches();

        final BondDto afterWithdrawal = investApiBondClient.getByTicker(RUB_LOW);
        assertThat(afterWithdrawal.getCreditRatings(), hasSize(1));
        assertThat(afterWithdrawal.getCreditRating().getRating(), equalTo("AA-.ru"));
        assertThat(afterWithdrawal.getCreditRating().getScope(), equalTo(RatingScopeDto.ISSUE));
        assertThat(jdbcTemplate.queryForObject("select count(*) from credit_rating where withdrawn", Integer.class), equalTo(1));
    }

    @Test
    void ratingHistoryByPages() {
        // страница 1 - свежие, страница 2 - последний релиз старше срока действия рейтинга: дальше не читаем
        mockAcra(ACRA_URL + "?PAGEN_1=1",
                acraRow("8001", "АКРА ПОДТВЕРДИЛО КРЕДИТНЫЙ РЕЙТИНГ ПАО «АВТО ФИНАНС БАНК» НА УРОВНЕ A(RU), ПРОГНОЗ «СТАБИЛЬНЫЙ»",
                        "ПАО «Авто Финанс Банк»", today.minusDays(3)));
        mockAcra(ACRA_URL + "?PAGEN_1=2",
                acraRow("8002", "АКРА ПРИСВОИЛО ВЫПУСКУ ОБЛИГАЦИЙ АО «ПУШКИНСКОЕ ПЗ» (RU000A1080Y2) КРЕДИТНЫЙ РЕЙТИНГ BB+(RU)",
                        "АО «Пушкинское ПЗ»", today.minusMonths(6)),
                acraRow("8003", "АКРА ПРИСВОИЛО ВЫПУСКУ ОБЛИГАЦИЙ ОФЗ (RU000A1038V6) КРЕДИТНЫЙ РЕЙТИНГ AAA(RU)",
                        "Минфин России", today.minusMonths(20)));
        mockNkr(nkrRow("AFB-OLD", "НКР присвоило ПАО «Авто Финанс Банк» кредитный рейтинг B.ru", today.minusMonths(24)),
                nkrRow("AFB-NEW", "НКР подтвердило кредитный рейтинг ПАО «Авто Финанс Банк» на уровне A-.ru, прогноз — стабильный",
                        today.minusMonths(2)));

        assertThat(creditRatingSyncUseCase.loadHistory(audit), equalTo(3));

        verify(newsHttpClient, never()).get(ACRA_URL + "?PAGEN_1=3");
        clearCaches();
        assertThat(investApiBondClient.getByTicker(RUB_LOW).getCreditRatings().stream().map(CreditRatingDto::getRating).toList(),
                contains("A(RU)", "A-.ru"));
        assertThat(investApiBondClient.getByTicker(RUB_MODERATE).getCreditRating().getRating(), equalTo("BB+(RU)"));
        assertThat(investApiBondClient.getByTicker(OFZ).getCreditRating(), nullValue());
    }

    @Test
    void durationLiquiditySpreadAndFloaterBase() {
        // свечи: у RU000A10EQ34 два дня в окне, у ОФЗ - один, у флоатера - только два месяца назад
        insertCandle(RUB_LOW, today.minusDays(1), "100.5", 100);
        insertCandle(RUB_LOW, today.minusDays(3), "99.5", 300);
        insertCandle(OFZ, today.minusDays(2), "61", 50);
        insertCandle(FLOATER, today.minusDays(60), "95", 10);
        jdbcTemplate.update("insert into bond_coupon_base (isin, coupon_details, updated_at) values (?, ?, now()), (?, ?, now())",
                FLOATER, "Ключевая ставка", "RU000A1038V6", "Фиксированный с известными купонами");
        clearCaches();

        // по обороту: торговых дней биржи в окне три, у флоатера сделок не было - 0, без свечей вовсе - в конце
        final List<BondDto> byTurnover = page(new BondParametersRequest()
                .setBondSorts(sort(BondSortFieldRequest.AVG_DAILY_TURNOVER, BondSortOrderRequest.DESC))).getContent();
        assertThat(tickers(byTurnover).subList(0, 3), contains(RUB_LOW, OFZ, FLOATER));
        final BondDto rubLow = byTurnover.getFirst();
        assertThat(rubLow.getLiquidity().getTradingDays(), equalTo(3));
        assertThat(rubLow.getLiquidity().getPeriodDays(), equalTo(30));
        // (100 + 300) / 3 штук; (100 × 100,5% + 300 × 99,5%) × 1000 / 3 дня
        assertThat(rubLow.getLiquidity().getAvgDailyVolume(), closeTo(new BigDecimal("133.33"), PRECISION));
        assertThat(rubLow.getLiquidity().getAvgDailyTurnover().getQuantity(), closeTo(new BigDecimal("133000.00"), PRECISION));
        assertThat(rubLow.getLiquidity().getAvgDailyTurnover().getCurrency(), equalTo("rub"));
        assertThat(rubLow.getLiquidity().getAvgDailyTurnoverRub(), closeTo(new BigDecimal("133000.00"), PRECISION));
        assertThat(byTurnover.get(2).getLiquidity().getAvgDailyTurnoverRub(), closeTo(BigDecimal.ZERO, PRECISION));
        assertThat(byTicker(byTurnover, RUB_MODERATE).getLiquidity(), nullValue());

        // спред к ОФЗ: доходность минус кривая на срок дюрации; у валютной облигации - нет
        final BigDecimal curveAt7 = ScreenerCalculationTest.moexCurve().yieldAt(7.5).orElseThrow().setScale(2, RoundingMode.HALF_UP);
        final BondDto ofz = byTicker(byTurnover, OFZ);
        assertThat(ofz.getOfzCurveYield(), closeTo(curveAt7, PRECISION));
        assertThat(ofz.getSpreadToOfz(), closeTo(new BigDecimal("15.00").subtract(curveAt7), PRECISION));
        assertThat(ofz.getCoupon().getDurationMacaulay(), closeTo(new BigDecimal("7.5"), PRECISION));
        assertThat(ofz.getCoupon().getDurationModified(), closeTo(new BigDecimal("6.52"), PRECISION));
        assertThat(rubLow.getSpreadToOfz(), greaterThan(BigDecimal.ZERO));
        assertThat(byTicker(byTurnover, USD).getSpreadToOfz(), nullValue());
        assertThat(byTicker(byTurnover, USD).getCoupon().getDurationMacaulay(), notNullValue());
        // база флоатера - только у флоатера, премии нет
        assertThat(byTicker(byTurnover, FLOATER).getFloaterBaseRate(), equalTo("Ключевая ставка"));
        assertThat(byTicker(byTurnover, FLOATER).getFloaterPremium(), nullValue());
        assertThat(ofz.getFloaterBaseRate(), nullValue());

        // карточка - те же данные
        final BondDto card = investApiBondClient.getByTicker(OFZ);
        assertThat(card.getSpreadToOfz(), equalTo(ofz.getSpreadToOfz()));
        assertThat(card.getLiquidity().getAvgDailyVolume(), closeTo(new BigDecimal("16.67"), PRECISION));

        // фильтры по новым полям: без значения облигация не подходит
        assertThat(tickers(filter(new BondParametersRequest().setSpreadToOfz(range("0", null)))), contains(RUB_LOW));
        assertThat(tickers(filter(new BondParametersRequest().setDuration(range(null, "2")))), contains(USD, RUB_LOW));
        assertThat(tickers(filter(new BondParametersRequest().setYieldToMaturity(range("16", null)))), contains(RUB_LOW));
        assertThat(tickers(filter(new BondParametersRequest().setAvgDailyTurnover(range("1", null)))), contains(RUB_LOW, OFZ));
        assertThat(tickers(filter(new BondParametersRequest().setSectors(List.of("FINANCIAL")))), contains(RUB_LOW));
        assertThat(tickers(filter(new BondParametersRequest().setCouponFrequencies(List.of(12, 4)))), contains(FLOATER, RUB_LOW));
        assertThat(tickers(filter(new BondParametersRequest().setHasOffer(true))), contains(RUB_LOW));
        assertThat(tickers(filter(new BondParametersRequest().setHasAmortization(true))), contains(FLOATER));
        assertThat(tickers(filter(new BondParametersRequest().setYearsToMaturity(range(null, "1")))), contains(CNY));
        assertThat(filter(new BondParametersRequest().setHasOffer(false)).stream().map(BondDto::getTicker).toList().contains(RUB_LOW),
                equalTo(false));

        // сортировка по дюрации: без дюрации - в конце при любом направлении
        assertThat(tickers(page(new BondParametersRequest().setBondSorts(sort(BondSortFieldRequest.DURATION,
                BondSortOrderRequest.ASC))).getContent()).subList(0, 3), contains(USD, RUB_LOW, OFZ));
        assertThat(tickers(page(new BondParametersRequest().setBondSorts(sort(BondSortFieldRequest.SPREAD_TO_OFZ,
                BondSortOrderRequest.DESC))).getContent()).subList(0, 2), contains(RUB_LOW, OFZ));

        assertThat(investApiBondClient.getSectors(), contains("consumer", "energy", "financial", "government", "materials"));
    }

    @Test
    void durationIsStoredWithYieldOnRecalculation() {
        jdbcTemplate.update("update price set aci_value = 0, aci_currency = 'rub' where ticker = ?", RUB_LOW);
        final String uid = jdbcTemplate.queryForObject("select uid from bond where ticker = ?", String.class, RUB_LOW);

        couponInterestUseCase.recalculate(Set.of(uid), audit);

        final Map<String, Object> coupon = jdbcTemplate.queryForMap(
                "select yield_to_maturity, duration_macaulay, duration_modified from coupon where ticker = ?", RUB_LOW);
        final BigDecimal macaulay = (BigDecimal) coupon.get("duration_macaulay");
        assertThat(coupon.get("yield_to_maturity"), notNullValue());
        assertThat(macaulay, greaterThan(BigDecimal.ZERO));
        // выплаты не позже погашения 14.03.2029 - дюрация меньше срока
        assertThat(macaulay.doubleValue(), lessThan(
                (double) ChronoUnit.DAYS.between(today, LocalDate.of(2029, 3, 14)) / 365));
        assertThat((BigDecimal) coupon.get("duration_modified"), lessThan(macaulay));
    }

    @Test
    void floaterBaseIsLoadedWithNews() {
        when(moexIssClient.getBondMarketSecurities(any())).thenReturn(Map.of("securities", new IssTableDto(
                List.of("ISIN", "COUPON_DETAILS"),
                List.of(List.of(FLOATER, "Ключевая ставка"), List.of(FLOATER, "Ключевая ставка"),
                        List.of("RU000NOTOURS", "Ставка RUONIA")))));

        newsSyncUseCase.syncAll(audit);

        assertThat(jdbcTemplate.queryForList("select isin || ':' || coupon_details from bond_coupon_base", String.class),
                contains(FLOATER + ":Ключевая ставка"));
    }

    @Test
    void exportCsvAndXlsx() throws Exception {
        final String body = "{\"sectors\":[\"financial\",\"government\"],\"bondSorts\":[{\"sortField\":\"TICKER\"}]}";

        final HttpResponse<byte[]> csv = post("/internal/rest/bonds/all/export?format=CSV", body);
        assertThat(csv.statusCode(), equalTo(200));
        assertThat(csv.headers().firstValue("Content-Type").orElseThrow(), startsWith("text/csv"));
        assertThat(csv.headers().firstValue("Content-Disposition").orElseThrow(),
                containsString("attachment; filename=\"bonds-all-"));
        final String[] lines = new String(csv.body(), StandardCharsets.UTF_8).split("\r\n");
        assertThat(lines.length, equalTo(3));
        assertThat(lines[1], startsWith(RUB_LOW + ";"));
        assertThat(lines[2], startsWith(OFZ + ";"));

        final HttpResponse<byte[]> xlsx = post("/internal/rest/bonds/local/export?format=XLSX", body);
        assertThat(xlsx.statusCode(), equalTo(200));
        assertThat(xlsx.headers().firstValue("Content-Type").orElseThrow(),
                equalTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(xlsx.headers().firstValue("Content-Disposition").orElseThrow(), containsString(".xlsx"));
        // zip
        assertThat(new String(Arrays.copyOf(xlsx.body(), 2), StandardCharsets.US_ASCII), equalTo("PK"));

        assertThat(post("/internal/rest/bonds/all/export?format=PDF", body).statusCode(), equalTo(BAD_REQUEST));
    }

    private HttpResponse<byte[]> post(final String path, final String body) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:8099" + path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private PageDto<BondDto> page(final BondParametersRequest request) {
        return investApiBondClient.getAllPage(0, 100, request);
    }

    private List<BondDto> filter(final BondParametersRequest request) {
        return page(request.setBondSorts(sort(BondSortFieldRequest.TICKER, BondSortOrderRequest.ASC))).getContent();
    }

    private static List<BondSortRequest> sort(final BondSortFieldRequest field, final BondSortOrderRequest order) {
        final BondSortRequest sort = new BondSortRequest();
        sort.setSortField(field);
        sort.setSortOrder(order);
        return List.of(sort);
    }

    private static ValueRangeRequest range(final String min, final String max) {
        final ValueRangeRequest range = new ValueRangeRequest();
        range.setMin(min == null ? null : new BigDecimal(min));
        range.setMax(max == null ? null : new BigDecimal(max));
        return range;
    }

    private void setYield(final String ticker, final String yield, final String macaulay, final String modified) {
        jdbcTemplate.update("update coupon set yield_to_maturity = ?, duration_macaulay = ?, duration_modified = ? where ticker = ?",
                new BigDecimal(yield), new BigDecimal(macaulay), new BigDecimal(modified), ticker);
    }

    private void insertIssuer(final String isin, final long emitterId, final String title) {
        jdbcTemplate.update("insert into bond_issuer (isin, emitter_id, title, updated_at) values (?, ?, ?, now())", isin, emitterId, title);
    }

    private void insertCandle(final String ticker, final LocalDate date, final String close, final long volume) {
        jdbcTemplate.update("""
                insert into price_candle (bond_id, uid, candle_date, open, high, low, close, volume, created_at)
                select id, uid, ?, ?, ?, ?, ?, ?, now() from bond where ticker = ?""",
                date, new BigDecimal(close), new BigDecimal(close), new BigDecimal(close), new BigDecimal(close), volume, ticker);
    }

    private void mockAcra(final String url, final String... rows) {
        when(newsHttpClient.get(url)).thenReturn(("<html><head><meta charset=\"utf-8\"></head><body>" + String.join("", rows)
                + "</body></html>").getBytes(StandardCharsets.UTF_8));
    }

    private static String acraRow(final String id, final String title, final String issuer, final LocalDate date) {
        return """
                <div class="documents-row search-table-row"><div class="documents-row__wrapper">
                  <div class="documents-row__item" data-type="name"><span class="item__emit"> %s </span>
                    <div class="item__title-row"><a class="item__title" href="/press-releases/%s/"> %s </a></div></div>
                  <div class="documents-row__item" data-type="date"> %s </div>
                </div></div>
                """.formatted(issuer, id, title,
                date.getDayOfMonth() + " " + RU_MONTHS[date.getMonthValue() - 1] + " " + date.getYear());
    }

    private void mockNkr(final String... rows) {
        when(newsHttpClient.get(NKR_URL)).thenReturn(("<html><head><meta charset=\"utf-8\"></head><body><table>"
                + String.join("", rows) + "</table></body></html>").getBytes(StandardCharsets.UTF_8));
    }

    private static String nkrRow(final String id, final String title, final LocalDate date) {
        return """
                <tr><td><a href="/ratings/press-releases/%s/" class="blue-link">%s</a></td>
                    <td><span class="mt-descr">Банки</span></td><td><span class="mt-descr">%s</span></td></tr>
                """.formatted(id, title, DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date));
    }

    private static Map<String, IssTableDto> curveResponse() {
        return Map.of("params", new IssTableDto(
                List.of("tradedate", "tradetime", "B1", "B2", "B3", "T1", "G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8", "G9"),
                List.of(List.of("2026-11-13", "18:54:59", 1579.580772, -355.786825, 155.676185, 2.0, 12.606815, -8.545117,
                        -13.221452, -5.479985, 10.435647, 8.708645, -3.194020, 0.0, 0.0))));
    }

    private void clearCaches() {
        cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames()
                .forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)));
    }

    private static BondDto byTicker(final List<BondDto> bonds, final String ticker) {
        return bonds.stream().filter(bond -> ticker.equals(bond.getTicker())).findFirst().orElseThrow();
    }

    private static List<String> tickers(final List<BondDto> bonds) {
        return bonds.stream().map(BondDto::getTicker).toList();
    }
}
