package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;

import java.util.List;
import java.util.Map;

public interface TinkoffCouponDataRepositoryUseCase {
    Map<String, List<CouponDataModel>> saveCouponData(Map<String, List<CouponDataModel>> couponBatch);
}
