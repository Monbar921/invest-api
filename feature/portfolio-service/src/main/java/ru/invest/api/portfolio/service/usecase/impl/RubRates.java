package ru.invest.api.portfolio.service.usecase.impl;

import ru.invest.api.tinkoff.supplier.service.PriceConversionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Курсы к рублю на один запрос портфеля: каждая пара «валюта, дата» запрашивается один раз, в том числе неудачно.
 * Дата null - курс на сегодня (ЦБ, иначе budget.org), иначе - курс ЦБ на эту дату.
 */
public final class RubRates {
    private static final int MONEY_SCALE = 2;

    private final PriceConversionService priceConversionService;
    private final Map<String, Optional<BigDecimal>> rates = new HashMap<>();

    public RubRates(final PriceConversionService priceConversionService) {
        this.priceConversionService = priceConversionService;
    }

    /** Сумма в рублях, округлённая до копеек; null - суммы, валюты или курса нет */
    public BigDecimal toRub(final BigDecimal amount, final String currency, final LocalDate date) {
        final BigDecimal exact = toRubExact(amount, currency, date);
        return exact == null ? null : exact.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Сумма в рублях без округления - для потоков, которые потом складываются */
    public BigDecimal toRubExact(final BigDecimal amount, final String currency, final LocalDate date) {
        if (amount == null || currency == null) {
            return null;
        }
        final String code = currency.toUpperCase();
        return rates.computeIfAbsent(code + "@" + date, key -> Optional.ofNullable(date == null
                        ? priceConversionService.getRubRate(code)
                        : priceConversionService.getRubRate(code, date)))
                .map(amount::multiply)
                .orElse(null);
    }
}
