package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;

import java.util.List;
import java.util.Map;

public interface TinkoffCouponRepositoryUseCase {
    CouponModel getCoupon(BondModel bondModel);

    CouponModel saveCoupon(CouponModel couponModel);

    Map<String, List<CouponDataModel>> saveCoupon(Map<String, List<CouponDataModel>> couponBatch);
}
