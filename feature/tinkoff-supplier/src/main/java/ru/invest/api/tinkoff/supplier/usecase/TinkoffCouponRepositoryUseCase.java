package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.CouponModel;

public interface TinkoffCouponRepositoryUseCase {
    CouponModel getCouponByUid(String uid);
}
