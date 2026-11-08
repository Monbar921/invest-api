package ru.invest.api.currency.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.invest.api.common.mapper.CurrencyMapper;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.currency.service.provider.CurrencyProvider;
import ru.invest.api.currency.service.usecase.CurrencyPriceUseCase;
import ru.invest.api.currency.service.usecase.CurrencyRateHistoryUseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CurrencyPriceUseCaseImpl implements CurrencyPriceUseCase {
    private static final Set<String> RUB_CODES = Set.of("RUB", "RUR");
    private static final int RATE_SCALE = 10;

    private final CurrencyMapper currencyMapper;
    private final CurrencyProvider currencyProvider;
    private final CurrencyRateHistoryUseCase currencyRateHistoryUseCase;


    @Override
    public CurrencyModel calculateAmount(final String baseCurrency, final String targetCurrency, final BigDecimal amount) {
        final CurrencyModel currencyModel = currencyMapper.clone(currencyProvider.getCurrency(baseCurrency, targetCurrency));
        // курс не нашёлся ни у ЦБ, ни у budget.org
        if (currencyModel == null || currencyModel.getRate() == null) {
            return null;
        }

        final BigDecimal newAmount = Optional.ofNullable(amount)
                .map(num -> num.multiply(currencyModel.getRate()))
                .orElse(null);

        return currencyModel.setRate(newAmount);
    }

    @Override
    public BigDecimal getRate(final String baseCurrency, final String targetCurrency, final LocalDate date) {
        if (baseCurrency == null || targetCurrency == null) {
            return null;
        }
        if (RUB_CODES.contains(targetCurrency.toUpperCase())) {
            return currencyRateHistoryUseCase.getRubRate(baseCurrency, date);
        }
        if (RUB_CODES.contains(baseCurrency.toUpperCase())) {
            return Optional.ofNullable(currencyRateHistoryUseCase.getRubRate(targetCurrency, date))
                    .filter(rate -> rate.signum() > 0)
                    .map(rate -> BigDecimal.ONE.divide(rate, RATE_SCALE, RoundingMode.HALF_UP))
                    .orElse(null);
        }
        return null;
    }
}
