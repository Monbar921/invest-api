package ru.invest.api.bond.supplier.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;

import java.util.List;

public interface BondUseCase {
    List<BondModel> getForeignCurrencyBonds(BondParametersModel bondParametersModel);
}
