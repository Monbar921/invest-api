package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;

public interface TinkoffCouponRepositoryUseCase {
    CouponModel getCoupon(BondModel bondModel);

    CouponModel saveCoupon(CouponModel couponModel);
}
