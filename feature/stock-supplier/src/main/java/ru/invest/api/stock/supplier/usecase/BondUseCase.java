package ru.invest.api.stock.supplier.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParameters;

import java.util.List;

public interface BondUseCase {
    List<BondModel> getForeignCurrencyBonds(BondParameters bondParameters);
}
