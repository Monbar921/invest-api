package ru.invest.api.currency.service.provider.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.invest.api.budget.org.supplier.usecase.BudgetOrgCurrencyUseCase;
import ru.invest.api.cb.rf.supplier.usecase.CbRfCurrencyUseCase;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.currency.service.provider.CurrencyProvider;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import static ru.invest.api.common.constants.CacheConstants.CURRENCY_PROVIDER_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.CURRENCY_PROVIDER_CACHE_NAME;
import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_CURRENCY;

@Component
@RequiredArgsConstructor
public class CurrencyProviderImpl implements CurrencyProvider {
    private static final List<String> RUB_CODES = List.of("RUB", "RUR");

    private final CbRfCurrencyUseCase cbRfCurrencyUseCase;
    private final BudgetOrgCurrencyUseCase budgetOrgCurrencyUseCase;

    @Override
    @Cacheable(value = CURRENCY_PROVIDER_CACHE_NAME, cacheManager = CURRENCY_PROVIDER_CACHE_MANAGER)
    public CurrencyModel getCurrency(final String baseCurrency, final String targetCurrency) {
        if (StringUtils.isBlank(baseCurrency) || StringUtils.isBlank(targetCurrency)) {
            throw new GeneralUnprocessableEntityException(EMPTY_CURRENCY, "Provide currency codes");
        }

        if (RUB_CODES.contains(baseCurrency.toUpperCase()) || RUB_CODES.contains(targetCurrency.toUpperCase())) {
            final LocalDate localDate = LocalDate.now(ZoneOffset.UTC);
            final List<CurrencyModel> currencies = cbRfCurrencyUseCase.getCurrencies(localDate);

            final CurrencyModel rubCurrency = getCurrency(baseCurrency, targetCurrency, currencies);
            if (rubCurrency != null) {
                return rubCurrency;
            }
        }


        return budgetOrgCurrencyUseCase.getCurrency(baseCurrency, targetCurrency);
    }

    private CurrencyModel getCurrency(final String baseCurrency, final String targetCurrency, final List<CurrencyModel> currencies) {
        if (CollectionUtils.isNotEmpty(currencies)) {
            return currencies
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(currency -> StringUtils.isNotBlank(currency.getBase()) && StringUtils.isNotBlank(currency.getTarget()))
                    .filter(currency -> StringUtils.equalsIgnoreCase(baseCurrency, currency.getBase()))
                    .filter(currency -> StringUtils.equalsIgnoreCase(targetCurrency, currency.getTarget()))
                    .findAny()
                    .orElse(null);
        }

        return null;
    }
}
