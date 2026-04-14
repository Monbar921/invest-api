package ru.invest.api.stock.supplier.usecase;

import ru.invest.api.common.model.BondModel;

import java.util.List;

public interface BondUseCase {
    List<BondModel> getForeignCurrencyBonds();
}
