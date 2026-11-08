package ru.invest.api.feature.currency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;
import ru.invest.api.cb.rf.supplier.model.CurrencyDto;
import ru.invest.api.cb.rf.supplier.model.CurrencyElementDto;
import ru.invest.api.currency.service.usecase.CurrencyRateHistoryUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Курсы ЦБ на дату: кэш → таблица currency_rate → ЦБ.
 */
@Sql(statements = "truncate table currency_rate restart identity")
public class CurrencyRateTest extends AbstractInvestApplicationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);
    private static final String DATE_REQ = "15/01/2026";
    private static final DateTimeFormatter CB_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private CurrencyRateHistoryUseCase rateHistoryUseCase;
    // тот же мок, что зарегистрирован через @MockitoBean в AbstractInvestApplicationTest
    @Autowired
    private CbRfClient cbRfClient;
    @Autowired
    private List<CacheManager> cacheManagers;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clearCaches();
        clearInvocations(cbRfClient);
        // ЦБ: доллар 80 ₽, юань - 112 ₽ за 10
        when(cbRfClient.getCurrencyRates(any())).thenReturn(rates(rate("USD", "1", "80"), rate("CNY", "10", "112")));
    }

    @Test
    void firstRequestStoresAllCurrenciesOfTheDate() {
        assertThat(rateHistoryUseCase.getRubRate("usd", DATE), comparesEqualTo(new BigDecimal("80")));

        verify(cbRfClient, times(1)).getCurrencyRates(DATE_REQ);
        assertThat(jdbcTemplate.queryForList("select currency from currency_rate where rate_date = ?", String.class,
                DATE), containsInAnyOrder("USD", "CNY"));
        // курс за единицу: номинал ЦБ уже учтён
        assertThat(storedRate("CNY", DATE), comparesEqualTo(new BigDecimal("11.2")));
    }

    @Test
    void storedRateIsReadFromTableWithoutCallingCb() {
        rateHistoryUseCase.getRubRate("USD", DATE);
        // как после перезапуска: в памяти ничего нет
        clearCaches();
        clearInvocations(cbRfClient);

        assertThat(rateHistoryUseCase.getRubRate("CNY", DATE), comparesEqualTo(new BigDecimal("11.2")));
        assertThat(rateHistoryUseCase.getRubRate("USD", DATE), comparesEqualTo(new BigDecimal("80")));
        verify(cbRfClient, never()).getCurrencyRates(any());
    }

    @Test
    void tableRateWinsOverCb() {
        jdbcTemplate.update("""
                insert into currency_rate (rate_date, currency, rate, source, created_at)
                values (?, 'USD', 77.5, 'CB_RF', now())""", DATE);

        assertThat(rateHistoryUseCase.getRubRate("USD", DATE), comparesEqualTo(new BigDecimal("77.5")));
        verify(cbRfClient, never()).getCurrencyRates(any());
    }

    @Test
    void futureDateIsNotStored() {
        final LocalDate tomorrow = LocalDate.now(ZoneId.of("Europe/Moscow")).plusDays(1);

        assertThat(rateHistoryUseCase.getRubRate("USD", tomorrow), comparesEqualTo(new BigDecimal("80")));
        verify(cbRfClient).getCurrencyRates(CB_FORMAT.format(tomorrow));
        assertThat(jdbcTemplate.queryForObject("select count(*) from currency_rate", Integer.class), equalTo(0));
    }

    @Test
    void unknownCurrencyAndRuble() {
        assertThat(rateHistoryUseCase.getRubRate("XYZ", DATE), nullValue());
        // остальные валюты даты всё равно сохранены - следующий запрос не пойдёт к ЦБ
        assertThat(storedRate("USD", DATE), comparesEqualTo(new BigDecimal("80")));

        clearInvocations(cbRfClient);
        assertThat(rateHistoryUseCase.getRubRate("RUB", DATE), comparesEqualTo(BigDecimal.ONE));
        verify(cbRfClient, never()).getCurrencyRates(any());
    }

    private BigDecimal storedRate(final String currency, final LocalDate date) {
        return jdbcTemplate.queryForObject("select rate from currency_rate where currency = ? and rate_date = ?",
                BigDecimal.class, currency, date);
    }

    private void clearCaches() {
        cacheManagers.forEach(cacheManager -> cacheManager.getCacheNames()
                .forEach(name -> Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear)));
    }

    private static CurrencyDto rates(final CurrencyElementDto... rates) {
        final CurrencyDto dto = new CurrencyDto();
        dto.setCurrencies(List.of(rates));
        return dto;
    }

    private static CurrencyElementDto rate(final String code, final String nominal, final String rubles) {
        final CurrencyElementDto rate = new CurrencyElementDto();
        rate.setCharCode(code);
        rate.setNominal(new BigDecimal(nominal));
        rate.setRate(new BigDecimal(rubles));
        return rate;
    }
}
