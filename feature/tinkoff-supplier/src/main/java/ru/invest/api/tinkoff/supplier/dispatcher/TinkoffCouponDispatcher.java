package ru.invest.api.tinkoff.supplier.dispatcher;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;

import java.util.List;

public interface TinkoffCouponDispatcher {
    List<CouponModel> getCoupons(List<BondModel> bonds);
}
