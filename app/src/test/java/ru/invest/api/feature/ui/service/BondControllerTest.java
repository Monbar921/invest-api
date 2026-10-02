package ru.invest.api.feature.ui.service;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;
import ru.invest.api.cb.rf.supplier.model.CurrencyDto;
import ru.invest.api.cb.rf.supplier.model.CurrencyElementDto;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.usecase.PriceSyncUseCase;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.bond.CouponDataDto;
import ru.invest.api.dto.bond.enums.RiskLevelDto;
import ru.invest.api.dto.page.PageDto;
import ru.invest.api.dto.request.bond.BondParametersRequest;
import ru.invest.api.dto.request.bond.BondSortFieldRequest;
import ru.invest.api.dto.request.bond.BondSortOrderRequest;
import ru.invest.api.dto.request.bond.BondSortRequest;
import ru.invest.api.dto.request.bond.ValueRangeRequest;
import ru.invest.api.starter.client.InvestApiBondClient;
import ru.invest.api.tinkoff.supplier.wrapper.MarketDataGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.Quotation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

/**
 * Пользователи работают с приложением только через BondController, поэтому все проверки идут через InvestApiBondClient -
 * настоящий HTTP-вызов к поднятому приложению. Данные в БД кладёт фикстура, как их положили бы фоновые шедулеры.
 */
@Sql(scripts = "/sql/bonds-for-controller.sql")
public class BondControllerTest extends AbstractInvestApplicationTest {
    // ОФЗ, rub, LOW, цена 610.00
    private static final String OFZ = "SU26238RMFS4";
    // rub, MODERATE, цена 940.00
    private static final String RUB_MODERATE = "RU000A1080Y2";
    // rub, HIGH, цена 950.50
    private static final String RUB_HIGH = "RU000A10EHC1";
    // rub, LOW, цена 1005.00
    private static final String RUB_LOW = "RU000A10EQ34";
    // usd, LOW, цена 980.00 USD = 88 200 RUB
    private static final String USD_LOW = "RU000A105A95";
    // cny, MODERATE, цена 1001.00 CNY = 12 512.50 RUB
    private static final String CNY_MODERATE = "RU000A106Z77";
    // rub, HIGH, текущей цены ещё нет
    private static final String RUB_WITHOUT_PRICE = "RU000A10ECY6";

    private static final int BAD_REQUEST = 400;
    private static final int NOT_FOUND = 404;
    // курсы ЦБ: 1 USD = 90 RUB, 10 CNY = 125 RUB (у юаня номинал 10, как бывает у ЦБ)
    private static final CurrencyElementDto USD_RATE = cbRfRate("USD", "1", "90,0");
    private static final CurrencyElementDto CNY_RATE = cbRfRate("CNY", "10", "125,0");

    @Autowired
    private InvestApiBondClient investApiBondClient;
    @Autowired
    private PriceSyncUseCase priceSyncUseCase;
    @Autowired
    private AuditMapper auditMapper;
    // тот же мок, что зарегистрирован через @MockitoBean в AbstractInvestApplicationTest
    @Autowired
    private MarketDataGrpcRateLimitedWrapper marketDataGrpcWrapper;

    // те же моки, что зарегистрированы через @MockitoBean в AbstractInvestApplicationTest
    @Autowired
    private CbRfClient cbRfClient;
    @Autowired
    private List<CacheManager> cacheManagers;

    @BeforeEach
    public void setUp() {
        // контроллер читает облигации и курсы через кэши - иначе он отдал бы данные предыдущих тестов
        cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames()
                .forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)));

        mockCbRfRates(USD_RATE, CNY_RATE);
    }

    @Test
    public void getAllWithoutParametersSortsByCurrentPriceTest() {
        final List<BondDto> bonds = investApiBondClient.getAll(null);

        // по умолчанию - по текущей цене в рублях по возрастанию, облигации без цены в конце
        assertThat(tickers(bonds), contains(OFZ, RUB_MODERATE, RUB_HIGH, RUB_LOW, CNY_MODERATE, USD_LOW, RUB_WITHOUT_PRICE));
    }

    @Test
    public void getAllMapsBondFieldsTest() {
        final BondDto bond = investApiBondClient.getAll(null)
                .stream()
                .filter(dto -> RUB_LOW.equals(dto.getTicker()))
                .findFirst()
                .orElseThrow();

        assertThat(bond.getUid(), equalTo("1c0a2f3e-0004-4000-8000-000000000004"));
        assertThat(bond.getIsin(), equalTo("RU000A10EQ34"));
        assertThat(bond.getName(), equalTo("Авто Финанс Банк БО-001Р-18"));
        assertThat(bond.getRiskLevel(), equalTo(RiskLevelDto.RISK_LEVEL_LOW));
        assertThat(bond.getMaturityDate().withZoneSameInstant(ZoneOffset.UTC).toLocalDate(), equalTo(LocalDate.of(2029, 3, 14)));

        assertThat(bond.getPrice(), notNullValue());
        assertThat(bond.getPrice().getNominal().getQuantity(), comparesEqualTo(new BigDecimal("1000.00")));
        assertThat(bond.getPrice().getNominal().getCurrency(), equalTo("rub"));
        assertThat(bond.getPrice().getCurrent().getQuantity(), comparesEqualTo(new BigDecimal("1005.00")));
        assertThat(bond.getPrice().getCurrent().getCurrency(), equalTo("rub"));

        // купон отдаётся без графика выплат
        assertThat(bond.getCoupon(), notNullValue());
        assertThat(bond.getCoupon().getUid(), equalTo("1c0a2f3e-0004-4000-8000-000000000004"));
        assertThat(bond.getCoupon().getInterest(), comparesEqualTo(new BigDecimal("18.50")));
        assertThat(bond.getCoupon().getQuantityPerYear(), equalTo(12));
        assertThat(bond.getCoupon().getCouponData(), nullValue());

        // 1005.00 от номинала 1000.00
        assertThat(bond.getPrice().getPercentagePrice(), comparesEqualTo(new BigDecimal("100.5")));
    }

    @Test
    public void getAllWithoutCurrentPriceReturnsOnlyNominalTest() {
        final BondDto bond = investApiBondClient.getAll(null)
                .stream()
                .filter(dto -> RUB_WITHOUT_PRICE.equals(dto.getTicker()))
                .findFirst()
                .orElseThrow();

        assertThat(bond.getPrice().getNominal().getQuantity(), comparesEqualTo(new BigDecimal("1000.00")));
        assertThat(bond.getPrice().getCurrent(), nullValue());
        assertThat(bond.getPrice().getPercentagePrice(), nullValue());
        assertThat(bond.getCoupon(), nullValue());
    }

    @Test
    public void filterByPercentagePriceTest() {
        final BondParametersRequest request = new BondParametersRequest();
        request.setPercentagePrice(valueRange("95", "100"));

        final List<BondDto> bonds = investApiBondClient.getAll(null, request);

        // проценты от номинала не зависят от валюты: RUB_MODERATE 98.9474%, RUB_HIGH 95.05%, USD_LOW 98%;
        // не проходят OFZ 61%, CNY_MODERATE 100.1%, RUB_LOW 100.5%; облигация без цены - в конце
        assertThat(tickers(bonds), contains(RUB_MODERATE, RUB_HIGH, USD_LOW, RUB_WITHOUT_PRICE));
    }

    @Test
    public void sortByPriceDescTest() {
        final List<BondDto> bonds = investApiBondClient.getAll(null, sortedBy(sort(BondSortFieldRequest.PRICE, BondSortOrderRequest.DESC)));

        // по цене в рублях: 88 200, 12 512.50, 1005, 950.50, 940, 610; облигация без цены - в конце и при DESC
        assertThat(tickers(bonds), contains(USD_LOW, CNY_MODERATE, RUB_LOW, RUB_HIGH, RUB_MODERATE, OFZ, RUB_WITHOUT_PRICE));
    }

    @Test
    public void sortByPercentagePriceTest() {
        final List<BondDto> bonds = investApiBondClient.getAll(null, sortedBy(sort(BondSortFieldRequest.PERCENTAGE_PRICE, BondSortOrderRequest.ASC)));

        // 61%, 95.05%, 98%, 98.9474%, 100.1%, 100.5%; облигация без цены - в конце
        assertThat(tickers(bonds), contains(OFZ, RUB_HIGH, USD_LOW, RUB_MODERATE, CNY_MODERATE, RUB_LOW, RUB_WITHOUT_PRICE));
    }

    @Test
    public void getAllBatchLimitTest() {
        final List<BondDto> bonds = investApiBondClient.getAll(2);

        assertThat(tickers(bonds), contains(OFZ, RUB_MODERATE));
    }

    @Test
    public void getAllNotPositiveBatchLimitIsRejectedTest() {
        final FeignException exception = assertThrows(FeignException.class, () -> investApiBondClient.getAll(0));

        assertThat(exception.status(), equalTo(BAD_REQUEST));
        assertThat(exception.contentUTF8(), containsString("batchLimit must be a positive number"));
    }

    @Test
    public void getForeignTest() {
        final List<BondDto> bonds = investApiBondClient.getForeign(null);

        // 12 512.50 RUB за CNY-облигацию меньше, чем 88 200 RUB за USD-облигацию
        assertThat(tickers(bonds), contains(CNY_MODERATE, USD_LOW));
    }

    @Test
    public void foreignBondWithoutRateIsTreatedAsWithoutPriceTest() {
        // ЦБ не вернул курс юаня, запасной источник курсов (budget.org) тоже ничего не дал
        mockCbRfRates(USD_RATE);

        final List<BondDto> bonds = investApiBondClient.getForeign(null);

        assertThat(tickers(bonds), contains(USD_LOW, CNY_MODERATE));
        // в ответе цена остаётся в исходной валюте
        final BondDto cnyBond = findByTicker(bonds, CNY_MODERATE);
        assertThat(cnyBond.getPrice().getCurrent().getQuantity(), comparesEqualTo(new BigDecimal("1001.00")));
        assertThat(cnyBond.getPrice().getCurrent().getCurrency(), equalTo("cny"));
    }

    @Test
    public void getLocalTest() {
        final List<BondDto> bonds = investApiBondClient.getLocal(null);

        assertThat(tickers(bonds), contains(OFZ, RUB_MODERATE, RUB_HIGH, RUB_LOW, RUB_WITHOUT_PRICE));
    }

    @Test
    public void filterByCurrentPriceTest() {
        final BondParametersRequest request = new BondParametersRequest();
        request.setCurrentPrice(valueRange("900", "1000"));

        final List<BondDto> bonds = investApiBondClient.getAll(null, request);

        // диапазон в рублях: USD-облигация (88 200 RUB) не проходит, хотя её цена в долларах 980;
        // облигацию без текущей цены фильтр по цене не отсекает - её цена ещё неизвестна
        assertThat(tickers(bonds), contains(RUB_MODERATE, RUB_HIGH, RUB_WITHOUT_PRICE));
    }

    @Test
    public void filterByCurrentPriceInRubForForeignBondTest() {
        final BondParametersRequest request = new BondParametersRequest();
        request.setCurrentPrice(valueRange("10000", "20000"));

        final List<BondDto> bonds = investApiBondClient.getAll(null, request);

        assertThat(tickers(bonds), contains(CNY_MODERATE, RUB_WITHOUT_PRICE));
    }

    @Test
    public void filterByCurrentPricePutsUnknownPriceLastForAnySortTest() {
        final BondParametersRequest request = sortedBy(sort(BondSortFieldRequest.TICKER, BondSortOrderRequest.ASC));
        request.setCurrentPrice(valueRange("900", "1000"));

        final List<BondDto> bonds = investApiBondClient.getAll(null, request);

        // по тикеру облигация без цены (RU000A10ECY6) стояла бы второй, но при фильтре по цене она уходит в конец
        assertThat(tickers(bonds), contains(RUB_MODERATE, RUB_HIGH, RUB_WITHOUT_PRICE));
    }

    @Test
    public void filterByCurrentPriceWithBatchLimitSkipsUnknownPriceTest() {
        final BondParametersRequest request = sortedBy(sort(BondSortFieldRequest.TICKER, BondSortOrderRequest.ASC));
        request.setCurrentPrice(valueRange("900", "1000"));

        final List<BondDto> bonds = investApiBondClient.getAll(2, request);

        assertThat(tickers(bonds), contains(RUB_MODERATE, RUB_HIGH));
    }

    @Test
    public void sortByCouponInterestDescTest() {
        final List<BondDto> bonds = investApiBondClient.getAll(null, sortedBy(
                sort(BondSortFieldRequest.COUPON_INTEREST, BondSortOrderRequest.DESC),
                sort(BondSortFieldRequest.TICKER, BondSortOrderRequest.ASC)));

        // облигации без купона - в конце, между собой по тикеру
        assertThat(tickers(bonds), contains(RUB_HIGH, RUB_LOW, OFZ, USD_LOW, CNY_MODERATE, RUB_MODERATE, RUB_WITHOUT_PRICE));
    }

    @Test
    public void priceSyncIsVisibleWithoutWaitingForCacheTest() {
        // первый запрос кладёт облигации в кэш
        final BondDto before = findByTicker(investApiBondClient.getAll(null), RUB_LOW);
        assertThat(before.getPrice().getCurrent().getQuantity(), comparesEqualTo(new BigDecimal("1005.00")));

        when(marketDataGrpcWrapper.getLastPrices(any())).thenReturn(GetLastPricesResponse.newBuilder()
                .addLastPrices(LastPrice.newBuilder()
                        .setInstrumentUid(before.getUid())
                        .setPrice(Quotation.newBuilder().setUnits(99)))
                .build());
        priceSyncUseCase.syncByTicker(RUB_LOW, auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));

        // синхронизация сбросила кэш - пользователь сразу видит новую цену: 99% от номинала 1000
        final BondDto after = findByTicker(investApiBondClient.getAll(null), RUB_LOW);
        assertThat(after.getPrice().getCurrent().getQuantity(), comparesEqualTo(new BigDecimal("990.00")));
    }

    @Test
    public void filterByRiskLevelTest() {
        final BondParametersRequest request = new BondParametersRequest();
        request.setRiskLevels(List.of(RiskLevelDto.RISK_LEVEL_LOW));

        final List<BondDto> bonds = investApiBondClient.getAll(null, request);

        assertThat(tickers(bonds), contains(OFZ, RUB_LOW, USD_LOW));
    }

    @Test
    public void filterOnlyOfzTest() {
        final BondParametersRequest request = new BondParametersRequest();
        request.setIsOfz(true);

        final List<BondDto> bonds = investApiBondClient.getAll(null, request);

        assertThat(tickers(bonds), contains(OFZ));
    }

    @Test
    public void filterWithoutOfzTest() {
        final BondParametersRequest request = new BondParametersRequest();
        request.setIsOfz(false);

        final List<BondDto> bonds = investApiBondClient.getAll(null, request);

        assertThat(tickers(bonds), containsInAnyOrder(RUB_MODERATE, RUB_HIGH, RUB_LOW, USD_LOW, CNY_MODERATE, RUB_WITHOUT_PRICE));
    }

    @Test
    public void sortByTickerDescTest() {
        final List<BondDto> bonds = investApiBondClient.getAll(null, sortedBy(sort(BondSortFieldRequest.TICKER, BondSortOrderRequest.DESC)));

        assertThat(tickers(bonds), contains(OFZ, RUB_LOW, RUB_HIGH, RUB_WITHOUT_PRICE, RUB_MODERATE, CNY_MODERATE, USD_LOW));
    }

    @Test
    public void sortByMaturityDateTest() {
        final List<BondDto> bonds = investApiBondClient.getAll(null, sortedBy(sort(BondSortFieldRequest.MATURITY_DATE, BondSortOrderRequest.ASC)));

        assertThat(tickers(bonds), contains(CNY_MODERATE, USD_LOW, RUB_MODERATE, RUB_LOW, RUB_WITHOUT_PRICE, RUB_HIGH, OFZ));
    }

    @Test
    public void sortByRiskLevelThenTickerTest() {
        final List<BondDto> bonds = investApiBondClient.getAll(null, sortedBy(
                sort(BondSortFieldRequest.RISK_LEVEL, BondSortOrderRequest.ASC),
                sort(BondSortFieldRequest.TICKER, BondSortOrderRequest.ASC)));

        // сначала надёжные (LOW и MODERATE), потом HIGH; внутри группы - по тикеру
        assertThat(tickers(bonds), contains(USD_LOW, CNY_MODERATE, RUB_MODERATE, RUB_LOW, OFZ, RUB_WITHOUT_PRICE, RUB_HIGH));
    }

    @Test
    public void filterAndSortForLocalBondsTest() {
        final BondParametersRequest request = sortedBy(sort(BondSortFieldRequest.TICKER, BondSortOrderRequest.ASC));
        request.setRiskLevels(List.of(RiskLevelDto.RISK_LEVEL_LOW, RiskLevelDto.RISK_LEVEL_MODERATE));

        final List<BondDto> bonds = investApiBondClient.getLocal(2, request);

        assertThat(tickers(bonds), contains(RUB_MODERATE, RUB_LOW));
    }

    @Test
    public void getAllPageSplitsSortedBondsTest() {
        final PageDto<BondDto> first = investApiBondClient.getAllPage(0, 3, new BondParametersRequest());
        final PageDto<BondDto> second = investApiBondClient.getAllPage(1, 3, new BondParametersRequest());
        final PageDto<BondDto> last = investApiBondClient.getAllPage(2, 3, new BondParametersRequest());

        // тот же порядок, что у getAll без параметров, разрезанный по 3
        assertThat(tickers(first.getContent()), contains(OFZ, RUB_MODERATE, RUB_HIGH));
        assertThat(tickers(second.getContent()), contains(RUB_LOW, CNY_MODERATE, USD_LOW));
        assertThat(tickers(last.getContent()), contains(RUB_WITHOUT_PRICE));

        assertThat(second.getPage(), equalTo(1));
        assertThat(second.getSize(), equalTo(3));
        assertThat(second.getTotalElements(), equalTo(7L));
        assertThat(second.getTotalPages(), equalTo(3));
    }

    @Test
    public void getAllPageDefaultsTest() {
        final PageDto<BondDto> page = investApiBondClient.getAllPage(null, null, new BondParametersRequest());

        assertThat(page.getPage(), equalTo(0));
        assertThat(page.getSize(), equalTo(50));
        assertThat(page.getContent(), hasSize(7));
    }

    @Test
    public void getAllPageCountsOnlyFilteredBondsTest() {
        final BondParametersRequest request = new BondParametersRequest();
        request.setRiskLevels(List.of(RiskLevelDto.RISK_LEVEL_LOW));

        final PageDto<BondDto> page = investApiBondClient.getAllPage(1, 2, request);

        // LOW: OFZ, RUB_LOW, USD_LOW - на второй странице остаётся одна
        assertThat(tickers(page.getContent()), contains(USD_LOW));
        assertThat(page.getTotalElements(), equalTo(3L));
        assertThat(page.getTotalPages(), equalTo(2));
    }

    @Test
    public void getAllPageBeyondLastIsEmptyTest() {
        final PageDto<BondDto> page = investApiBondClient.getAllPage(5, 3, new BondParametersRequest());

        assertThat(page.getContent(), empty());
        assertThat(page.getTotalElements(), equalTo(7L));
    }

    @Test
    public void getAllPageIsNotLimitedByDefaultBatchLimitTest() {
        // batchLimit по умолчанию (100) ограничивает только списочные эндпоинты: страница считает всю выдачу
        final PageDto<BondDto> page = investApiBondClient.getAllPage(0, 1, sortedBy(sort(BondSortFieldRequest.TICKER, BondSortOrderRequest.DESC)));

        assertThat(tickers(page.getContent()), contains(OFZ));
        assertThat(page.getTotalElements(), equalTo(7L));
    }

    @Test
    public void getForeignAndLocalPagesTest() {
        final PageDto<BondDto> foreign = investApiBondClient.getForeignPage(0, 10, new BondParametersRequest());
        final PageDto<BondDto> local = investApiBondClient.getLocalPage(0, 2, new BondParametersRequest());

        assertThat(tickers(foreign.getContent()), contains(CNY_MODERATE, USD_LOW));
        assertThat(foreign.getTotalElements(), equalTo(2L));
        assertThat(tickers(local.getContent()), contains(OFZ, RUB_MODERATE));
        assertThat(local.getTotalElements(), equalTo(5L));
    }

    @Test
    public void getAllPageInvalidParametersAreRejectedTest() {
        final FeignException negativePage = assertThrows(FeignException.class,
                () -> investApiBondClient.getAllPage(-1, 10, new BondParametersRequest()));
        final FeignException tooBigSize = assertThrows(FeignException.class,
                () -> investApiBondClient.getAllPage(0, 501, new BondParametersRequest()));

        assertThat(negativePage.status(), equalTo(BAD_REQUEST));
        assertThat(negativePage.contentUTF8(), containsString("page must not be negative"));
        assertThat(tooBigSize.status(), equalTo(BAD_REQUEST));
        assertThat(tooBigSize.contentUTF8(), containsString("size must not exceed 500"));
    }

    @Test
    public void getByTickerReturnsCouponScheduleTest() {
        final BondDto bond = investApiBondClient.getByTicker(RUB_LOW);

        assertThat(bond.getName(), equalTo("Авто Финанс Банк БО-001Р-18"));
        assertThat(bond.getPrice().getCurrent().getQuantity(), comparesEqualTo(new BigDecimal("1005.00")));
        assertThat(bond.getPrice().getPercentagePrice(), comparesEqualTo(new BigDecimal("100.5")));
        assertThat(bond.getCoupon().getInterest(), comparesEqualTo(new BigDecimal("18.50")));

        // в фикстуре выплаты вставлены не по порядку - карточка отдаёт их по дате выплаты
        final List<CouponDataDto> couponData = bond.getCoupon().getCouponData();
        assertThat(couponData.stream().map(data -> data.getPaymentDate().getMonthValue()).toList(), contains(10, 11, 12));
        assertThat(couponData.getLast().getPrice().getQuantity(), comparesEqualTo(new BigDecimal("15.71")));
        assertThat(couponData.getLast().getPrice().getCurrency(), equalTo("rub"));
    }

    @Test
    public void getByTickerWithoutCouponTest() {
        final BondDto bond = investApiBondClient.getByTicker(RUB_WITHOUT_PRICE);

        assertThat(bond.getCoupon(), nullValue());
        assertThat(bond.getPrice().getCurrent(), nullValue());
    }

    @Test
    public void getByUnknownTickerIsNotFoundTest() {
        final FeignException exception = assertThrows(FeignException.class, () -> investApiBondClient.getByTicker("UNKNOWN"));

        assertThat(exception.status(), equalTo(NOT_FOUND));
    }

    private void mockCbRfRates(final CurrencyElementDto... rates) {
        final CurrencyDto currencyDto = new CurrencyDto();
        currencyDto.setCurrencies(List.of(rates));
        when(cbRfClient.getCurrencyRates(any())).thenReturn(currencyDto);
    }

    private static CurrencyElementDto cbRfRate(final String charCode, final String nominal, final String rubles) {
        final CurrencyElementDto rate = new CurrencyElementDto();
        rate.setCharCode(charCode);
        rate.setNominal(new BigDecimal(nominal));
        rate.setRate(new BigDecimal(rubles.replace(',', '.')));
        return rate;
    }

    private BondDto findByTicker(final List<BondDto> bonds, final String ticker) {
        return bonds.stream()
                .filter(bond -> ticker.equals(bond.getTicker()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> tickers(final List<BondDto> bonds) {
        return bonds.stream()
                .map(BondDto::getTicker)
                .toList();
    }

    private BondParametersRequest sortedBy(final BondSortRequest... sorts) {
        final BondParametersRequest request = new BondParametersRequest();
        request.setBondSorts(List.of(sorts));
        return request;
    }

    private BondSortRequest sort(final BondSortFieldRequest field, final BondSortOrderRequest order) {
        final BondSortRequest sort = new BondSortRequest();
        sort.setSortField(field);
        sort.setSortOrder(order);
        return sort;
    }

    private ValueRangeRequest valueRange(final String min, final String max) {
        final ValueRangeRequest range = new ValueRangeRequest();
        range.setMin(new BigDecimal(min));
        range.setMax(new BigDecimal(max));
        return range;
    }
}
