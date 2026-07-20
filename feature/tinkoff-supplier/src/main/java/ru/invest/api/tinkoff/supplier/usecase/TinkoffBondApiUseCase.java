package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;

import java.util.Map;

public interface TinkoffBondApiUseCase {
    Map<String, BondModel> getAllBonds();
}
