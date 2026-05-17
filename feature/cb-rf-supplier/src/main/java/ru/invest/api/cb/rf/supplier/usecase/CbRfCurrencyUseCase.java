package ru.invest.api.cb.rf.supplier.usecase;

import ru.invest.api.common.model.CurrencyModel;

import java.time.LocalDate;
import java.util.List;

public interface CbRfCurrencyUseCase {
    List<CurrencyModel> getCurrencies(LocalDate localDate);
}
