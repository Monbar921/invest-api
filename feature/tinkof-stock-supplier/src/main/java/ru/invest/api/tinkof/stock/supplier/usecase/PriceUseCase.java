package ru.invest.api.tinkof.stock.supplier.usecase;

import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public interface PriceUseCase {
    <T> Map<String, PriceModel> getLastPrices(List<String> uids, Map<String, T> specificModels,
                                              BiFunction<Map<String, T>, String, MoneyValue> nominalGetter);
}
