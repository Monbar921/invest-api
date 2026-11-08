package ru.invest.api.currency.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import ru.invest.api.cb.rf.supplier.usecase.CbRfCurrencyUseCase;
import ru.invest.api.common.entity.CurrencyRate;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.repository.CurrencyRateRepository;
import ru.invest.api.currency.service.usecase.CurrencyRateHistoryUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static ru.invest.api.common.constants.CacheConstants.CURRENCY_RATE_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.CURRENCY_RATE_CACHE_NAME;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyRateHistoryUseCaseImpl implements CurrencyRateHistoryUseCase {
    private static final String RUB = "RUB";
    private static final Set<String> RUB_CODES = Set.of(RUB, "RUR");
    // ЦБ устанавливает курсы по московскому времени
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final CurrencyRateRepository repository;
    private final CbRfCurrencyUseCase cbRfCurrencyUseCase;

    @Override
    @Cacheable(value = CURRENCY_RATE_CACHE_NAME, cacheManager = CURRENCY_RATE_CACHE_MANAGER, unless = "#result == null")
    public BigDecimal getRubRate(final String currency, final LocalDate date) {
        if (StringUtils.isBlank(currency) || date == null) {
            return null;
        }
        final String code = currency.toUpperCase();
        if (RUB_CODES.contains(code)) {
            return BigDecimal.ONE;
        }
        return repository.findByRateDateAndCurrency(date, code)
                .map(CurrencyRate::getRate)
                .orElseGet(() -> fetchAndStore(code, date));
    }

    /**
     * Все курсы ЦБ на дату: нужный возвращается, все сохраняются. Будущую дату ЦБ отдаёт последним установленным
     * курсом - такой курс ещё может стать другим, поэтому не сохраняется.
     */
    private BigDecimal fetchAndStore(final String code, final LocalDate date) {
        final List<CurrencyModel> toRub = cbRfCurrencyUseCase.getCurrencies(date).stream()
                .filter(Objects::nonNull)
                .filter(rate -> RUB.equalsIgnoreCase(rate.getTarget()) && StringUtils.isNotBlank(rate.getBase())
                        && rate.getRate() != null)
                .toList();
        if (!date.isAfter(LocalDate.now(MOSCOW))) {
            store(date, toRub);
        }
        return toRub.stream()
                .filter(rate -> code.equalsIgnoreCase(rate.getBase()))
                .map(CurrencyModel::getRate)
                .findFirst()
                .orElse(null);
    }

    private void store(final LocalDate date, final List<CurrencyModel> rates) {
        final Set<String> stored = repository.findByRateDate(date).stream()
                .map(CurrencyRate::getCurrency)
                .collect(Collectors.toSet());
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        final List<CurrencyRate> missing = rates.stream()
                .filter(rate -> !stored.contains(rate.getBase().toUpperCase()))
                .map(rate -> new CurrencyRate()
                        .setRateDate(date)
                        .setCurrency(rate.getBase().toUpperCase())
                        .setRate(rate.getRate())
                        .setSource(CurrencyRate.SOURCE_CB_RF)
                        .setCreatedAt(now))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        try {
            repository.saveAll(missing);
        } catch (final DataIntegrityViolationException e) {
            // параллельный запрос уже сохранил курсы на эту дату - они те же самые
            log.debug("Currency rates for {} are already stored: {}", date, e.getMessage());
        }
    }
}
