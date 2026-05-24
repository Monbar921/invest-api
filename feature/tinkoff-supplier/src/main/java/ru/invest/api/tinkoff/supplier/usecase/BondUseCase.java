package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;

import java.util.List;

public interface BondUseCase {
    List<BondModel> getForeignCurrencyBonds();
}
