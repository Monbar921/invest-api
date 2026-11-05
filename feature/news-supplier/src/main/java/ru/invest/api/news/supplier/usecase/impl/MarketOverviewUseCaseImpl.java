package ru.invest.api.news.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.invest.api.cb.rf.supplier.usecase.CbRfCurrencyUseCase;
import ru.invest.api.cb.rf.supplier.usecase.CbRfNewsUseCase;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.model.news.KeyRateModel;
import ru.invest.api.common.model.news.MarketOverviewModel;
import ru.invest.api.common.model.news.MarketOverviewModel.CurrencyRateModel;
import ru.invest.api.news.supplier.usecase.MarketOverviewUseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static ru.invest.api.common.constants.CacheConstants.MARKET_OVERVIEW_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.MARKET_OVERVIEW_CACHE_NAME;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketOverviewUseCaseImpl implements MarketOverviewUseCase {
    private static final List<String> CURRENCIES = List.of("USD", "EUR", "CNY");
    private static final String RUB = "RUB";
    // ставку меняют 8 раз в год: за полтора года точно есть прошлое значение
    private static final int KEY_RATE_HISTORY_DAYS = 550;
    // праздники: курс не меняется до 10 дней подряд
    private static final int MAX_DAYS_BACK = 12;
    private static final int RATE_SCALE = 4;

    private final CbRfNewsUseCase cbRfNewsUseCase;
    private final CbRfCurrencyUseCase cbRfCurrencyUseCase;
    private final DateTimeMapper dateTimeMapper;

    @Override
    // ЦБ не ответил совсем - не кэшируем пустой обзор, следующий запрос попробует снова
    @Cacheable(value = MARKET_OVERVIEW_CACHE_NAME, cacheManager = MARKET_OVERVIEW_CACHE_MANAGER,
            unless = "#result.keyRate == null && #result.currencies.isEmpty()")
    public MarketOverviewModel getOverview() {
        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        final MarketOverviewModel overview = new MarketOverviewModel();
        fillKeyRate(overview, today);
        overview.setCurrencies(CURRENCIES.stream()
                .map(code -> currency(code, today))
                .flatMap(Optional::stream)
                .toList());
        return overview;
    }

    /**
     * Ставка на сегодня, с какого дня она действует и какой была до этого. ЦБ не ответил - ставки в обзоре нет.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void fillKeyRate(final MarketOverviewModel overview, final LocalDate today) {
        final List<KeyRateModel> rates;
        try {
            rates = cbRfNewsUseCase.getKeyRates(today.minusDays(KEY_RATE_HISTORY_DAYS), today);
        } catch (final RuntimeException e) {
            log.warn("Key rate is not loaded: {}", e.getMessage());
            return;
        }
        if (rates.isEmpty()) {
            return;
        }

        final BigDecimal current = rates.getLast().getRate();
        int index = rates.size() - 1;
        while (index > 0 && rates.get(index - 1).getRate().compareTo(current) == 0) {
            index--;
        }
        overview.setKeyRate(current)
                .setKeyRateSince(rates.get(index).getDate())
                .setPreviousKeyRate(index > 0 ? rates.get(index - 1).getRate() : null);
    }

    /**
     * Курс на сегодня и изменение к прошлому установленному курсу: ищем назад первый день с другим курсом.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private Optional<CurrencyRateModel> currency(final String code, final LocalDate today) {
        try {
            final BigDecimal rate = rate(code, today);
            if (rate == null) {
                return Optional.empty();
            }

            BigDecimal previous = null;
            for (int day = 1; day <= MAX_DAYS_BACK && previous == null; day++) {
                final BigDecimal candidate = rate(code, today.minusDays(day));
                if (candidate != null && candidate.compareTo(rate) != 0) {
                    previous = candidate;
                }
            }
            return Optional.of(new CurrencyRateModel()
                    .setCode(code)
                    .setRate(rate.setScale(RATE_SCALE, RoundingMode.HALF_UP))
                    .setChange(previous == null ? BigDecimal.ZERO.setScale(RATE_SCALE)
                            : rate.subtract(previous).setScale(RATE_SCALE, RoundingMode.HALF_UP)));
        } catch (final RuntimeException e) {
            log.warn("Currency rate {} is not loaded: {}", code, e.getMessage());
            return Optional.empty();
        }
    }

    private BigDecimal rate(final String code, final LocalDate date) {
        return cbRfCurrencyUseCase.getCurrencies(date)
                .stream()
                .filter(currency -> code.equals(currency.getBase()) && RUB.equals(currency.getTarget()))
                .map(CurrencyModel::getRate)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
