package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.ShortProductModel;

import java.util.List;

public interface GetNeedToUpdateCouponsUseCase {
    List<ShortProductModel> getUidTickersToUpdate();
}
