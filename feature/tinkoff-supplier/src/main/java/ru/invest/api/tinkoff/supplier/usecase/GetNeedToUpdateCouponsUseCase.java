package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.Pair;

import java.util.List;

public interface GetNeedToUpdateCouponsUseCase {
    List<Pair<String, String>> getUidTickersToUpdate();
}
