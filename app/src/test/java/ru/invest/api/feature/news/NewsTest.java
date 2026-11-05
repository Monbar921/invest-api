package ru.invest.api.feature.news;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;
import ru.invest.api.cb.rf.supplier.model.CurrencyDto;
import ru.invest.api.cb.rf.supplier.model.CurrencyElementDto;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.repository.NewsRepository;
import ru.invest.api.dto.bond.event.BondEventDto;
import ru.invest.api.dto.bond.event.BondEventTypeDto;
import ru.invest.api.dto.bond.news.MarketOverviewDto;
import ru.invest.api.dto.bond.news.NewsBondDto;
import ru.invest.api.dto.bond.news.NewsDto;
import ru.invest.api.dto.bond.news.NewsSourceDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.news.supplier.client.feign.MoexIssClient;
import ru.invest.api.news.supplier.model.IssTableDto;
import ru.invest.api.news.supplier.usecase.NewsSyncUseCase;
import ru.invest.api.starter.client.InvestApiBondClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.invest.api.common.constants.CacheConstants.CB_RF_ALL_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.MARKET_OVERVIEW_CACHE_MANAGER;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

@Sql(scripts = "/sql/bonds-for-controller.sql", statements = {
        "truncate table news restart identity cascade",
        "truncate table bond_issuer restart identity"})
public class NewsTest extends AbstractInvestApplicationTest {
    private static final int NOT_FOUND = 404;
    private static final String AUTO_FINANCE = "RU000A10EQ34";
    // тот же эмитент, что у RU000A10EQ34
    private static final String AUTO_FINANCE_OTHER = "RU000A10ECY6";
    private static final String PUSHKINO = "RU000A1080Y2";
    private static final String GIDROMASH = "RU000A10EHC1";
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Autowired
    private NewsSyncUseCase newsSyncUseCase;
    @Autowired
    private NewsRepository newsRepository;
    @Autowired
    private AuditMapper auditMapper;
    @Autowired
    private DateTimeMapper dateTimeMapper;
    @Autowired
    private InvestApiBondClient investApiBondClient;
    @Autowired
    private CbRfClient cbRfClient;
    @Autowired
    private MoexIssClient moexIssClient;
    @Autowired
    @Qualifier(MARKET_OVERVIEW_CACHE_MANAGER)
    private CacheManager marketOverviewCacheManager;
    @Autowired
    @Qualifier(CB_RF_ALL_CACHE_MANAGER)
    private CacheManager cbRfCacheManager;

    @BeforeEach
    public void setUp() {
        // RSS ЦБ: BOM в начале, HTML в описании, одна новость старше срока хранения
        when(cbRfClient.getPressReleasesRss()).thenReturn(rss("""
                <item>
                  <title>Банк России снизил ключевую ставку до 16,50% годовых</title>
                  <link>https://www.cbr.ru/press/pr/?file=1</link>
                  <guid isPermaLink="false">docid_1</guid>
                  <description>&lt;!-- TypographyUse --&gt;&lt;p&gt;Совет директоров&amp;nbsp;Банка России &lt;b&gt;снизил&lt;/b&gt; ставку&lt;/p&gt;</description>
                  <pubDate>Fri, 23 Oct 2026 13:30:00 +0300</pubDate>
                </item>
                <item>
                  <title>Старый пресс-релиз</title>
                  <link>https://www.cbr.ru/press/pr/?file=0</link>
                  <guid isPermaLink="false">docid_0</guid>
                  <pubDate>Wed, 01 Jan 2025 10:00:00 +0300</pubDate>
                </item>
                """));
        when(cbRfClient.getEventsRss()).thenReturn(rss("""
                <item>
                  <title>Регулятор рекомендует банкам помогать заёмщикам</title>
                  <link>https://www.cbr.ru/press/event/?id=7</link>
                  <guid isPermaLink="false">7</guid>
                  <description />
                  <pubDate>Thu, 05 Nov 2026 14:03:00 +0300</pubDate>
                </item>
                """));

        when(moexIssClient.getSecurities(argThat(parameters -> parameters != null && "0".equals(parameters.get("start")))))
                .thenReturn(Map.of("securities", new IssTableDto(
                        List.of("isin", "emitent_id", "emitent_title", "emitent_inn"),
                        List.of(
                                List.of(AUTO_FINANCE, 202, "Акционерное общество \"Авто Финанс Банк\"", "7700000002"),
                                List.of(AUTO_FINANCE_OTHER, 202, "Акционерное общество \"Авто Финанс Банк\"", "7700000002"),
                                List.of(PUSHKINO, 101, "Общество с ограниченной ответственностью \"Пушкинское ПЗ\"", "5000000001"),
                                List.of(GIDROMASH, 303, "Публичное акционерное общество «Гидромашсервис»", "7800000003"),
                                // выпуска нет в системе - эмитент не сохраняется
                                List.of("RU000A0ZZZZ9", 404, "ПАО \"Чужой\"", "1")))));
        when(moexIssClient.getSiteNews(argThat(parameters -> parameters != null && "0".equals(parameters.get("start")))))
                .thenReturn(Map.of("sitenews", new IssTableDto(
                        List.of("id", "tag", "title", "published_at", "modified_at"),
                        List.of(
                                List.of(1004, "site", "05.11.2026, 16-22 (мск) изменены значения верхней границы ценового коридора "
                                        + "ценной бумаги RU000A10EQ34", "2026-11-05 16:22:46", "2026-11-05 16:30:08"),
                                List.of(1003, "site", "О порядке приобретения облигаций серии 001Р-03 ООО &quot;Пушкинское ПЗ&quot; "
                                        + "с 06 ноября 2026 год", "2026-11-05 16:05:20", "2026-11-05 16:05:20"),
                                List.of(1002, "site", "О приостановке торгов ценными бумагами", "2026-11-03 10:00:00",
                                        "2026-11-03 10:00:00"),
                                List.of(1001, "site", "Московская биржа подвела итоги торгов в октябре 2026 года",
                                        "2026-11-02 12:00:00", "2026-11-02 12:00:00")))));
        when(moexIssClient.getSiteNewsContent(anyLong(), any())).thenReturn(Map.of("content", new IssTableDto(
                List.of("id", "title", "published_at", "body"),
                List.of(List.of(1, "", "", "<p><b>Участникам торгов</b></p><p>Торги ценными бумагами прекращены.</p>")))));
        when(moexIssClient.getSiteNewsContent(longThat(id -> id == 1002L), any())).thenReturn(Map.of("content", new IssTableDto(
                List.of("id", "title", "published_at", "body"),
                List.of(List.of(1002, "", "", "<p>Приостановлены торги облигациями (ISIN RU000A10EQ34)</p>")))));
    }

    @Test
    public void syncAndFeedTest() {
        newsSyncUseCase.syncAll(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));
        // второй прогон - те же ответы: дублей нет, эмитенты не перезапрашиваются, тексты новостей - тоже
        newsSyncUseCase.syncAll(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));

        // ценовой коридор - технический шум, пресс-релиз старше 180 дней удалён
        assertThat(newsRepository.count(), equalTo(5L));
        verify(moexIssClient, times(2)).getSecurities(any());
        verify(moexIssClient, times(3)).getSiteNewsContent(anyLong(), any());

        final PageDto<NewsDto> feed = investApiBondClient.getNews(null, null, null);
        assertThat(feed.getTotalElements(), equalTo(5L));
        // новые первыми; время Мосбиржи - московское, хранится в UTC
        assertThat(feed.getContent().stream().map(NewsDto::getTitle).toList(), contains(
                "О порядке приобретения облигаций серии 001Р-03 ООО \"Пушкинское ПЗ\" с 06 ноября 2026 год",
                "Регулятор рекомендует банкам помогать заёмщикам",
                "О приостановке торгов ценными бумагами",
                "Московская биржа подвела итоги торгов в октябре 2026 года",
                "Банк России снизил ключевую ставку до 16,50% годовых"));
        assertThat(feed.getContent().getFirst().getPublishedAt().withZoneSameInstant(ZoneOffset.UTC).getHour(), equalTo(13));

        final NewsDto keyRate = feed.getContent().getLast();
        assertThat(keyRate.getSource(), equalTo(NewsSourceDto.CBR_PRESS));
        // анонс - текст без тегов и HTML-сущностей
        assertThat(keyRate.getSummary(), equalTo("Совет директоров Банка России снизил ставку"));
        assertThat(keyRate.getUrl(), equalTo("https://www.cbr.ru/press/pr/?file=1"));

        final NewsDto suspension = feed.getContent().get(2);
        assertThat(suspension.getUrl(), equalTo("https://www.moex.com/n1002"));
        // ISIN нашёлся в тексте; эмитент - по этому ISIN, форма собственности сокращена
        assertThat(suspension.getBonds().stream().map(NewsBondDto::getTicker).toList(), contains(AUTO_FINANCE));
        assertThat(suspension.getIssuers(), contains("АО \"Авто Финанс Банк\""));
        // общая новость биржи ни к кому не относится
        assertThat(feed.getContent().get(3).getBonds(), empty());

        final PageDto<NewsDto> moex = investApiBondClient.getNews(Set.of(NewsSourceDto.MOEX), 0, 2);
        assertThat(moex.getTotalElements(), equalTo(3L));
        assertThat(moex.getContent(), hasSize(2));
        assertThat(moex.getTotalPages(), equalTo(2));
    }

    @Test
    public void bondNewsTest() {
        newsSyncUseCase.syncAll(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));

        // по названию эмитента в кавычках
        assertThat(titles(PUSHKINO), contains(startsWith("О порядке приобретения облигаций")));
        // по ISIN выпуска и по ISIN другого выпуска того же эмитента
        assertThat(titles(AUTO_FINANCE), contains("О приостановке торгов ценными бумагами"));
        assertThat(titles(AUTO_FINANCE_OTHER), contains("О приостановке торгов ценными бумагами"));
        assertThat(titles(GIDROMASH), empty());

        final FeignException exception = assertThrows(FeignException.class,
                () -> investApiBondClient.getBondNews("UNKNOWN", null, null));
        assertThat(exception.status(), equalTo(NOT_FOUND));
    }

    @Test
    public void marketOverviewTest() {
        marketOverviewCacheManager.getCacheNames().forEach(name -> marketOverviewCacheManager.getCache(name).clear());
        cbRfCacheManager.getCacheNames().forEach(name -> cbRfCacheManager.getCache(name).clear());
        when(cbRfClient.callDailyInfo(anyString(), anyString())).thenReturn("""
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/"><soap:Body>
                <KeyRateXMLResponse xmlns="http://web.cbr.ru/"><KeyRateXMLResult><KeyRate xmlns="">
                <KR><DT>2026-10-27T00:00:00+03:00</DT><Rate>16.50</Rate></KR>
                <KR><DT>2026-10-26T00:00:00+03:00</DT><Rate>17.00</Rate></KR>
                <KR><DT>2026-10-28T00:00:00+03:00</DT><Rate>16.50</Rate></KR>
                <KR><DT>2026-09-15T00:00:00+03:00</DT><Rate>17.00</Rate></KR>
                </KeyRate></KeyRateXMLResult></KeyRateXMLResponse></soap:Body></soap:Envelope>
                """.strip().getBytes(StandardCharsets.UTF_8));
        // сегодня доллар 81,50, вчера и раньше - 81,20; других валют ЦБ не вернул
        final String today = DateTimeFormatter.ofPattern("dd/MM/yyyy").format(dateTimeMapper.getNow().toLocalDate());
        when(cbRfClient.getCurrencyRates(anyString())).thenAnswer(invocation ->
                currencies(today.equals(invocation.getArgument(0)) ? "81.50" : "81.20"));

        final MarketOverviewDto overview = investApiBondClient.getMarketOverview();

        assertThat(overview.getKeyRate(), comparesEqualTo(new BigDecimal("16.50")));
        // дни ЦБ приходят не по порядку - ставка действует с первого дня после смены
        assertThat(overview.getKeyRateSince(), equalTo(LocalDate.of(2026, 10, 27)));
        assertThat(overview.getPreviousKeyRate(), comparesEqualTo(new BigDecimal("17.00")));
        assertThat(overview.getCurrencies(), hasSize(1));
        assertThat(overview.getCurrencies().getFirst().getCode(), equalTo("USD"));
        assertThat(overview.getCurrencies().getFirst().getRate(), comparesEqualTo(new BigDecimal("81.50")));
        assertThat(overview.getCurrencies().getFirst().getChange(), comparesEqualTo(new BigDecimal("0.30")));
    }

    @Test
    public void marketOverviewWithoutCbRfTest() {
        marketOverviewCacheManager.getCacheNames().forEach(name -> marketOverviewCacheManager.getCache(name).clear());
        cbRfCacheManager.getCacheNames().forEach(name -> cbRfCacheManager.getCache(name).clear());
        // ЦБ недоступен: обзор пустой, но не ошибка - лента новостей на странице всё равно показывается
        final MarketOverviewDto overview = investApiBondClient.getMarketOverview();

        assertThat(overview.getKeyRate(), nullValue());
        assertThat(overview.getCurrencies(), empty());
    }

    @Test
    public void bondEventsTest() {
        final List<BondEventDto> events = investApiBondClient.getEvents(AUTO_FINANCE);

        // по возрастанию даты, погашение - последним, с суммой номинала
        assertThat(events, not(empty()));
        assertThat(events, equalTo(events.stream().sorted(Comparator.comparing(BondEventDto::getDate)).toList()));
        assertThat(events.getLast().getType(), equalTo(BondEventTypeDto.MATURITY));
        assertThat(events.getLast().getDate(), equalTo(LocalDate.of(2029, 3, 14)));
        assertThat(events.getLast().getAmount().getQuantity(), comparesEqualTo(new BigDecimal("1000")));
        // у купонов из графика - размер; 14.12.2026 (15,71) вырос после 14.11.2026 (15,21)
        events.stream()
                .filter(event -> event.getDate().equals(LocalDate.of(2026, 12, 14)))
                .findFirst()
                .ifPresent(december -> assertThat(december.getPreviousAmount().getQuantity(),
                        comparesEqualTo(new BigDecimal("15.21"))));
        assertThat(events.stream().map(BondEventDto::getType).toList(), not(hasItem(BondEventTypeDto.OFFER)));
    }

    private List<String> titles(final String ticker) {
        return investApiBondClient.getBondNews(ticker, null, null).getContent().stream().map(NewsDto::getTitle).toList();
    }

    private static byte[] rss(final String items) {
        final byte[] xml = ("""
                <?xml version="1.0" encoding="utf-8"?>
                <rss version="2.0"><channel><title>Банк России</title>
                %s
                </channel></rss>
                """.strip().formatted(items)).getBytes(StandardCharsets.UTF_8);
        final byte[] withBom = new byte[UTF8_BOM.length + xml.length];
        System.arraycopy(UTF8_BOM, 0, withBom, 0, UTF8_BOM.length);
        System.arraycopy(xml, 0, withBom, UTF8_BOM.length, xml.length);
        return withBom;
    }

    private static CurrencyDto currencies(final String usdRate) {
        final CurrencyElementDto usd = new CurrencyElementDto();
        usd.setCharCode("USD");
        usd.setNominal(BigDecimal.ONE);
        usd.setRate(new BigDecimal(usdRate));
        final CurrencyDto currencyDto = new CurrencyDto();
        currencyDto.setCurrencies(List.of(usd));
        return currencyDto;
    }
}
