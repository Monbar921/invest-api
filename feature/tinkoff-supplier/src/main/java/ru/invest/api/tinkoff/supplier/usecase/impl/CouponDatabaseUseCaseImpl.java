package ru.invest.api.tinkoff.supplier.usecase.impl;

import org.springframework.stereotype.Component;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.tinkoff.supplier.usecase.CouponDatabaseUseCase;

@Component
public class CouponDatabaseUseCaseImpl implements CouponDatabaseUseCase {
    @Override
    public CouponModel getCoupons(BondModel bondModel) {
        return null;
    }

    @Override
    public CouponModel saveCoupons(CouponModel couponModel) {
        return null;
    }
}
