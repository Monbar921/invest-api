package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.tinkoff.piapi.contract.v1.Bond;

public interface CouponDatabaseUseCase {
    CouponModel getCoupons(BondModel bondModel);

    CouponModel saveCoupons(CouponModel couponModel);
}
