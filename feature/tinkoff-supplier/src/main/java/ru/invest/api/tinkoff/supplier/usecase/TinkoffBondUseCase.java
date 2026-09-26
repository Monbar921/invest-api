package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;

import java.util.List;
import java.util.Map;

public interface TinkoffBondUseCase {
    List<BondModel> getAll();

    BondModel findByTicker(String ticker);

    void save(Map<String, BondModel> tinkoffBonds, AuditModel audit);
}
