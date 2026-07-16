package ru.invest.api.tinkoff.supplier.dispatcher;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;

public interface TinkoffCouponDispatcher {
    CouponModel getCoupon(BondModel bondModel);
}
