package ru.invest.api.currency.service.provider;

import ru.invest.api.common.model.CurrencyModel;

public interface CurrencyProvider {
    CurrencyModel getCurrency(String baseCurrency, String targetCurrency);
}
