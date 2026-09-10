package ru.invest.api.tinkoff.supplier.dispatcher.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffCouponDispatcher;
import ru.invest.api.tinkoff.supplier.service.CouponCalculationService;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponApiUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponRepositoryUseCase;

import java.util.Optional;

import static ru.invest.api.common.constants.CacheConstants.COUPON_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.COUPON_CACHE_NAME;

@Service
@RequiredArgsConstructor
public class TinkoffCouponDispatcherImpl implements TinkoffCouponDispatcher {
    private final TinkoffCouponApiUseCase tinkoffCouponApiUseCase;
    private final TinkoffCouponRepositoryUseCase tinkoffCouponRepositoryUseCase;
    private final CouponCalculationService couponCalculationService;

    @Override
    @Cacheable(value = COUPON_CACHE_NAME, cacheManager = COUPON_CACHE_MANAGER, key = "#bondModel.uid", condition = "#bondModel != null")
    public CouponModel getCoupon(final BondModel bondModel) {
        if (bondModel == null) {
            return null;
        }

        final CouponModel coupon = dispatchCoupon(bondModel);

        return coupon.setInterest(
                couponCalculationService.calculateInterest(coupon, bondModel)
        );
    }

    public CouponModel dispatchCoupon(final BondModel bondModel) {
        if (!needTryFetchFromDatabase(bondModel)) {
            return fetchCouponsFromTinkoffApi(bondModel);
        }

        return Optional.ofNullable(fetchCouponsFromDatabase(bondModel))
                .filter(databaseCoupon -> CollectionUtils.isNotEmpty(databaseCoupon.getCouponData()))
                .orElseGet(() -> fetchCouponsFromTinkoffApi(bondModel));
    }

    private CouponModel fetchCouponsFromDatabase(final BondModel bondModel) {
        return tinkoffCouponRepositoryUseCase.getCoupon(bondModel);
    }

    private CouponModel fetchCouponsFromTinkoffApi(final BondModel bondModel) {
        return tinkoffCouponApiUseCase.getCoupon(bondModel);
    }

    private boolean needTryFetchFromDatabase(final BondModel bondModel) {
        return Optional.ofNullable(bondModel.getCoupon())
                .map(CouponModel::getIsFixed)
                .orElse(false);
    }
}
