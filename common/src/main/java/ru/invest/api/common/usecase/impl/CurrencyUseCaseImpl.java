package ru.invest.api.common.usecase.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.usecase.CurrencyUseCase;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_CURRENCY;
import static ru.invest.api.common.exception.enums.ExceptionErrorCode.NOT_FOUND;

@Component
public class CurrencyUseCaseImpl implements CurrencyUseCase {
    private final Map<String, BigDecimal> RATES = Map.of(
            "USD", BigDecimal.valueOf(75.2346),
            "CNY", BigDecimal.valueOf(11.0334),
            "EUR", BigDecimal.valueOf(88.7303)
    );

    @Override
    public BigDecimal getCate(final String currency) {
        if (StringUtils.isBlank(currency)) {
            throw new GeneralUnprocessableEntityException(EMPTY_CURRENCY, "Provide currency code");
        }

        return Optional.ofNullable(RATES.get(currency))
                .orElseThrow(() -> new GeneralNotFoundEntityException(NOT_FOUND
                        , "Can not get rate for currency=%s".formatted(currency)));
    }
}
