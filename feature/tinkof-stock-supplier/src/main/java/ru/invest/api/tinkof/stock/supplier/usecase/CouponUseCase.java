package ru.invest.api.tinkof.stock.supplier.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.tinkoff.piapi.contract.v1.Bond;

public interface CouponUseCase {
    CouponModel getCoupons(BondModel bondModel, Bond bond);
}
