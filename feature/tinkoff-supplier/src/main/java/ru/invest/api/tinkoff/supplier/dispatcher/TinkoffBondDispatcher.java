package ru.invest.api.tinkoff.supplier.dispatcher;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;

import java.util.List;

public interface TinkoffBondDispatcher {
    List<BondModel> getForeignCurrencyBonds(BondParametersModel bondParameters);

    List<BondModel> getRubbleCurrencyBonds(BondParametersModel bondParameters);
}
