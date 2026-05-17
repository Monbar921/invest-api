package ru.invest.api.currency.service.usecase;

import ru.invest.api.common.model.CurrencyModel;

import java.math.BigDecimal;

public interface CurrencyUseCase {
    CurrencyModel calculateAmount(String baseCurrency, String targetCurrency, BigDecimal amount);
}
