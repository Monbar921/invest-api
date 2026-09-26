package ru.invest.api.tinkoff.supplier.provider;

import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;

import java.util.Map;

public interface TinkoffPriceProvider {
    Map<String, PriceModel> getLastPrices(Map<String, BondModel> bonds, AuditModel audit);
}
