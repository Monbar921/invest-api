package ru.invest.api.tinkoff.supplier.dispatcher.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.tinkoff.supplier.dispatcher.CouponDispatcher;
import ru.invest.api.tinkoff.supplier.usecase.CouponDatabaseUseCase;
import ru.invest.api.tinkoff.supplier.usecase.CouponExternalDataProviderUseCase;
import ru.tinkoff.piapi.contract.v1.Bond;

import static ru.invest.api.common.constants.CacheConstants.COUPON_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.COUPON_CACHE_NAME;

@Component
@RequiredArgsConstructor
public class CouponDispatcherImpl implements CouponDispatcher {
    private final CouponExternalDataProviderUseCase couponExternalDataProviderUseCase;
    private final CouponDatabaseUseCase couponDatabaseUseCase;

    @Override
    @Cacheable(value = COUPON_CACHE_NAME, cacheManager = COUPON_CACHE_MANAGER, key = "#bond.uid")
    public CouponModel getCoupons(final BondModel bondModel, final Bond bond) {
        if (bond == null) {
            return null;
        }

        final CouponModel databaseCoupon = couponDatabaseUseCase.getCoupons(bondModel);
        if (databaseCoupon == null) {
            final CouponModel fetchedCoupon = couponExternalDataProviderUseCase.getCoupons(bondModel, bond);
            return couponDatabaseUseCase.saveCoupons(fetchedCoupon);
        }
        return databaseCoupon;
    }
}
