package ru.invest.api.tinkoff.supplier.dispatcher;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.tinkoff.piapi.contract.v1.Bond;

public interface CouponDispatcher {
    CouponModel getCoupons(BondModel bondModel, Bond bond);
}
