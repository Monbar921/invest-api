package ru.invest.api.currency.service.usecase;

import ru.invest.api.common.model.CurrencyModel;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface CurrencyPriceUseCase {
    CurrencyModel calculateAmount(String baseCurrency, String targetCurrency, BigDecimal amount);

    /**
     * Курс пары с рублём по ЦБ на дату: сколько targetCurrency за одну единицу baseCurrency. Курсы на дату хранятся
     * в таблице currency_rate - см. {@link CurrencyRateHistoryUseCase}.
     *
     * @return null - курса на эту дату нет или в паре нет рубля (истории курсов у budget.org нет)
     */
    BigDecimal getRate(String baseCurrency, String targetCurrency, LocalDate date);
}
