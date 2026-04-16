package ru.invest.api.stock.supplier.usecase;

import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.Bond;

import java.util.List;
import java.util.Map;

public interface PriceUseCase {
    Map<String, PriceModel> getBondPrice(List<Bond> bonds);
}
