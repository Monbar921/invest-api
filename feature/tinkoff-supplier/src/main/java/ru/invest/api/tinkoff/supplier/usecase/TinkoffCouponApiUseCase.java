package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;

public interface TinkoffCouponApiUseCase {
    CouponModel getCoupon(BondModel bondModel);
}
