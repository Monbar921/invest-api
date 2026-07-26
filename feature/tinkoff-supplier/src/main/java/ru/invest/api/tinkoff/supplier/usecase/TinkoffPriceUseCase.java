package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;

import java.util.Map;

public interface TinkoffPriceUseCase {
    Map<String, PriceModel> getLastPrices(Map<String, BondModel> bonds);
}
