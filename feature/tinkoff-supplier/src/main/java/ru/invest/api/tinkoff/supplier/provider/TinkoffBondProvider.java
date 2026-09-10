package ru.invest.api.tinkoff.supplier.provider;

import ru.invest.api.common.model.BondModel;

import java.util.Map;

public interface TinkoffBondProvider {
    Map<String, BondModel> getAllBonds();
}
