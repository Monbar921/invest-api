package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;

import java.util.List;
import java.util.Map;

public interface TinkoffCouponRepositoryUseCase {
    CouponModel getCouponByUid(String uid);

    List<CouponModel> saveCouponData(Map<String, List<CouponDataModel>> couponBatch);
}
