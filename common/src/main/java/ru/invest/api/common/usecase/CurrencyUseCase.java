package ru.invest.api.common.usecase;

import java.math.BigDecimal;

public interface CurrencyUseCase {
    BigDecimal getRate(String currency);
}
