package ru.invest.api.currency.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.common.mapper.CurrencyMapper;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.currency.service.provider.CurrencyProvider;
import ru.invest.api.currency.service.usecase.CurrencyUseCase;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrencyUseCaseImpl implements CurrencyUseCase {
    private final CurrencyMapper currencyMapper;
    private final CurrencyProvider currencyProvider;


    @Override
    public CurrencyModel calculateAmount(final String baseCurrency, final String targetCurrency, final BigDecimal amount) {
        final CurrencyModel currencyModel = currencyMapper.clone(currencyProvider.getCurrency(baseCurrency, targetCurrency));
        final BigDecimal newAmount = Optional.ofNullable(amount)
                .map(num -> num.multiply(currencyModel.getRate()))
                .orElse(null);

        return currencyModel.setRate(newAmount);
    }
}
