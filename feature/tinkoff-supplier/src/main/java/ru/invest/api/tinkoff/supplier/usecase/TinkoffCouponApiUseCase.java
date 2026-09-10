package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;

import java.util.List;

public interface TinkoffCouponApiUseCase {
    List<CouponDataModel> getCouponData(String uid);
}
