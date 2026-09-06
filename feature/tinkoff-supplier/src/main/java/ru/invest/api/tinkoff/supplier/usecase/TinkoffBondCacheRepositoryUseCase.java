package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;

import java.util.Map;

public interface TinkoffBondCacheRepositoryUseCase {
    Map<String, BondModel> getForeignCurrencyBonds();

    Map<String, BondModel> getRubbleCurrencyBonds();

    Map<String, BondModel> getBonds();
}
