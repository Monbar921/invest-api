package ru.invest.api.common.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.mapper.CurrencyMapper;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.usecase.CurrencyUseCase;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_CURRENCY;
import static ru.invest.api.common.exception.enums.ExceptionErrorCode.NOT_FOUND;

@Component
@RequiredArgsConstructor
public class CurrencyUseCaseImpl implements CurrencyUseCase {
    private final CurrencyMapper currencyMapper;

    private final List<CurrencyModel> RATES = List.of(
            new CurrencyModel("RUB", "USD", BigDecimal.valueOf(75.2346)),
            new CurrencyModel("RUB", "CNY", BigDecimal.valueOf(11)),
            new CurrencyModel("RUB", "EUR", BigDecimal.valueOf(85.2346)),
            new CurrencyModel("USD", "RUB", BigDecimal.valueOf(0.013552)),
            new CurrencyModel("CNY", "RUB", BigDecimal.valueOf(0.092147)),
            new CurrencyModel("EUR", "RUB", BigDecimal.valueOf(0.011444))
    );

    @Override
    public CurrencyModel getCurrency(final String baseCurrency, final String targetCurrency) {
        if (StringUtils.isBlank(baseCurrency) || StringUtils.isBlank(targetCurrency)) {
            throw new GeneralUnprocessableEntityException(EMPTY_CURRENCY, "Provide currency codes");
        }

        return RATES
                .stream()
                .filter(rate -> rate.getBase().equalsIgnoreCase(baseCurrency) && rate.getTarget().equalsIgnoreCase(targetCurrency))
                .findAny()
                .map(currencyMapper::clone)
                .orElseThrow(() -> new GeneralNotFoundEntityException(NOT_FOUND
                        , "Can not get rate for baseCurrency=%s, targetCurrency=%s".formatted(baseCurrency, targetCurrency)));
    }

    @Override
    public CurrencyModel calculateAmount(final String baseCurrency, final String targetCurrency, final BigDecimal amount) {
        final CurrencyModel currencyModel = currencyMapper.clone(getCurrency(baseCurrency, targetCurrency));
        final BigDecimal newAmount = Optional.ofNullable(amount)
                .map(num -> num.multiply(currencyModel.getRate()))
                .orElse(null);

        return currencyModel.setRate(newAmount);
    }
}
