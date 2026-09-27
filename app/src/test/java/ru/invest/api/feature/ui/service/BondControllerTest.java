package ru.invest.api.feature.ui.service;

import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.dto.bond.BondDto;
import ru.invest.api.dto.bond.enums.RiskLevelDto;
import ru.invest.api.dto.request.bond.BondParametersRequest;
import ru.invest.api.dto.request.bond.BondSortFieldRequest;
import ru.invest.api.dto.request.bond.BondSortOrderRequest;
import ru.invest.api.dto.request.bond.BondSortRequest;
import ru.invest.api.dto.request.bond.ValueRangeRequest;
import ru.invest.api.starter.client.InvestApiBondClient;

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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static ru.invest.api.common.constants.CacheConstants.BOND_REPOSITORY_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.BOND_REPOSITORY_CACHE_NAME;

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
    // usd, LOW, цена 980.00
    private static final String USD_LOW = "RU000A105A95";
    // cny, MODERATE, цена 1001.00
    private static final String CNY_MODERATE = "RU000A106Z77";
    // rub, HIGH, текущей цены ещё нет
    private static final String RUB_WITHOUT_PRICE = "RU000A10ECY6";

    private static final int BAD_REQUEST = 400;

    @Autowired
    private InvestApiBondClient investApiBondClient;
    @Autowired
    @Qualifier(BOND_REPOSITORY_CACHE_MANAGER)
    private CacheManager bondRepositoryCacheManager;

    @BeforeEach
    public void clearBondCache() {
        // контроллер читает облигации через кэш - иначе он отдал бы данные предыдущих тестов
        Optional.ofNullable(bondRepositoryCacheManager.getCache(BOND_REPOSITORY_CACHE_NAME))
                .ifPresent(Cache::clear);
    }

    @Test
    public void getAllWithoutParametersSortsByCurrentPriceTest() {
        final List<BondDto> bonds = investApiBondClient.getAll(null);

        // по умолчанию - по текущей цене по возрастанию, облигации без цены в конце
        assertThat(tickers(bonds), contains(OFZ, RUB_MODERATE, RUB_HIGH, USD_LOW, CNY_MODERATE, RUB_LOW, RUB_WITHOUT_PRICE));
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

        assertThat(tickers(bonds), contains(USD_LOW, CNY_MODERATE));
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

        // облигацию без текущей цены фильтр по цене не отсекает - её цена ещё неизвестна
        assertThat(tickers(bonds), contains(RUB_MODERATE, RUB_HIGH, USD_LOW, RUB_WITHOUT_PRICE));
    }

    @Test
    public void filterByRiskLevelTest() {
        final BondParametersRequest request = new BondParametersRequest();
        request.setRiskLevels(List.of(RiskLevelDto.RISK_LEVEL_LOW));

        final List<BondDto> bonds = investApiBondClient.getAll(null, request);

        assertThat(tickers(bonds), contains(OFZ, USD_LOW, RUB_LOW));
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
