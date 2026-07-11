package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;

import java.util.List;

public interface TinkoffBondUseCase {
    List<BondModel> getForeignCurrencyBonds(BondParametersModel bondParameters);

    List<BondModel> getRubbleCurrencyBonds(BondParametersModel bondParameters);
}
