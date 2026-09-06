package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;

import java.util.Map;

public interface TinkoffBondSyncRepositoryUseCase {
    void sync(Map<String, BondModel> tinkoffBonds);
}
