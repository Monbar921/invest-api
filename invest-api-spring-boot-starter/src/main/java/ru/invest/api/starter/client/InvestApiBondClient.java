package ru.invest.api.starter.client;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.bond.BondSearchItemDto;
import ru.invest.api.dto.bond.calculation.ProfitCalculationDto;
import ru.invest.api.dto.bond.calculation.ProfitCalculationRequest;
import ru.invest.api.dto.bond.candle.CandleDto;
import ru.invest.api.dto.bond.candle.CandleIntervalDto;
import ru.invest.api.dto.bond.collection.BondCollectionDto;
import ru.invest.api.dto.bond.collection.BondCollectionPageDto;
import ru.invest.api.dto.bond.event.BondEventDto;
import ru.invest.api.dto.bond.issuer.IssuerDto;
import ru.invest.api.dto.bond.news.MarketOverviewDto;
import ru.invest.api.dto.bond.news.NewsDto;
import ru.invest.api.dto.bond.news.NewsSourceDto;
import ru.invest.api.dto.bond.sitemap.SitemapDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.dto.request.bond.BondParametersRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Клиент к BondController. Базовый путь /internal/rest задаётся в InvestApiBondClientAutoConfiguration.
 * <p>
 * Feign не отправляет null в теле запроса, поэтому bondParametersRequest в методах с телом обязателен:
 * для запроса без фильтров и сортировок передаётся пустой BondParametersRequest.
 */
public interface InvestApiBondClient {
    /**
     * Страница всех облигаций.
     *
     * @param page                  номер страницы, начиная с 0, null - первая
     * @param size                  размер страницы (1..500), null - 50
     * @param bondParametersRequest поля и направления сортировки, фильтры по цене, риску и ОФЗ
     */
    @PostMapping("/bonds/all/page")
    PageDto<BondDto> getAllPage(@RequestParam(value = "page", required = false) Integer page,
                                @RequestParam(value = "size", required = false) Integer size,
                                @RequestBody BondParametersRequest bondParametersRequest);

    /**
     * Страница облигаций в иностранной валюте.
     */
    @PostMapping("/bonds/foreign/page")
    PageDto<BondDto> getForeignPage(@RequestParam(value = "page", required = false) Integer page,
                                    @RequestParam(value = "size", required = false) Integer size,
                                    @RequestBody BondParametersRequest bondParametersRequest);

    /**
     * Страница рублёвых облигаций.
     */
    @PostMapping("/bonds/local/page")
    PageDto<BondDto> getLocalPage(@RequestParam(value = "page", required = false) Integer page,
                                  @RequestParam(value = "size", required = false) Integer size,
                                  @RequestBody BondParametersRequest bondParametersRequest);

    /**
     * Облигация с ценой и купоном вместе с графиком выплат.
     */
    @GetMapping("/bonds/{ticker}")
    BondDto getByTicker(@PathVariable("ticker") String ticker);

    /**
     * Нестрогий поиск облигаций по тикеру, ISIN или названию.
     *
     * @param limit сколько облигаций вернуть (1..50), null - 20
     */
    @GetMapping("/bonds/search")
    List<BondSearchItemDto> search(@RequestParam("query") String query,
                                   @RequestParam(value = "limit", required = false) Integer limit);

    /**
     * Доход по облигации за период владения.
     */
    @PostMapping("/bonds/{ticker}/calculation")
    ProfitCalculationDto calculateProfit(@PathVariable("ticker") String ticker, @RequestBody ProfitCalculationRequest request);

    /**
     * Свечи цены облигации в % от номинала.
     *
     * @param interval null - DAY; from, to - YYYY-MM-DD, null - вся история и сегодня
     */
    @GetMapping("/bonds/{ticker}/candles")
    List<CandleDto> getCandles(@PathVariable("ticker") String ticker,
                               @RequestParam(value = "interval", required = false) CandleIntervalDto interval,
                               @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to);

    /**
     * События облигации из её графика выплат и дат.
     */
    @GetMapping("/bonds/{ticker}/events")
    List<BondEventDto> getEvents(@PathVariable("ticker") String ticker);

    /**
     * Общая лента новостей.
     *
     * @param source источники, null - все
     */
    @GetMapping("/bonds/news")
    PageDto<NewsDto> getNews(@RequestParam(value = "source", required = false) Set<NewsSourceDto> source,
                             @RequestParam(value = "page", required = false) Integer page,
                             @RequestParam(value = "size", required = false) Integer size);

    /**
     * Новости облигации.
     */
    @GetMapping("/bonds/{ticker}/news")
    PageDto<NewsDto> getBondNews(@PathVariable("ticker") String ticker,
                                 @RequestParam(value = "page", required = false) Integer page,
                                 @RequestParam(value = "size", required = false) Integer size);

    /**
     * Ключевая ставка и курсы валют ЦБ.
     */
    @GetMapping("/bonds/market-overview")
    MarketOverviewDto getMarketOverview();

    /**
     * Эмитент с выпусками из системы.
     *
     * @param id id эмитента на Мосбирже
     */
    @GetMapping("/bonds/issuers/{id}")
    IssuerDto getIssuer(@PathVariable("id") Long id);

    /**
     * Новости эмитента.
     */
    @GetMapping("/bonds/issuers/{id}/news")
    PageDto<NewsDto> getIssuerNews(@PathVariable("id") Long id,
                                   @RequestParam(value = "page", required = false) Integer page,
                                   @RequestParam(value = "size", required = false) Integer size);

    /**
     * Подборки облигаций.
     */
    @GetMapping("/bonds/collections")
    List<BondCollectionDto> getCollections();

    /**
     * Подборка со страницей облигаций.
     *
     * @param size размер страницы (1..500), null - 50
     */
    @GetMapping("/bonds/collections/{slug}")
    BondCollectionPageDto getCollection(@PathVariable("slug") String slug,
                                        @RequestParam(value = "page", required = false) Integer page,
                                        @RequestParam(value = "size", required = false) Integer size);

    /**
     * Тикеры облигаций, эмитенты и подборки для sitemap.xml.
     */
    @GetMapping("/bonds/sitemap")
    SitemapDto getSitemap();
}
