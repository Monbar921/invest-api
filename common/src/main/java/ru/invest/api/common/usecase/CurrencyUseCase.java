package ru.invest.api.common.usecase;

import ru.invest.api.common.model.CurrencyModel;

import java.math.BigDecimal;

public interface CurrencyUseCase {
    CurrencyModel getCurrency(String baseCurrency, String targetCurrency);

    CurrencyModel calculateAmount(String baseCurrency, String targetCurrency, BigDecimal amount);
}
