package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.PriceModel;

import java.util.Map;

public interface TinkoffPriceUseCase {
    void saveCurrentPrices(Map<String, PriceModel> prices);
}
