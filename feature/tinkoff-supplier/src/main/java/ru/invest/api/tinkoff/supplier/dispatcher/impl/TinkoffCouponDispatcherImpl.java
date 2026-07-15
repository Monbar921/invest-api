package ru.invest.api.tinkoff.supplier.dispatcher.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffCouponDispatcher;
import ru.invest.api.tinkoff.supplier.service.CouponCalculationService;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponApiUseCase;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TinkoffCouponDispatcherImpl implements TinkoffCouponDispatcher {
    private final TinkoffCouponApiUseCase tinkoffCouponApiUseCase;
    private final CouponCalculationService couponCalculationService;

    //    TODO не забыть исправить кэш
    @Override
    public CouponModel getCoupon(final BondModel bondModel, final Integer quantityPerYear) {
        if (bondModel == null) {
            return null;
        }

        final CouponModel coupon = dispatchCoupon(bondModel, quantityPerYear);

        return coupon.setInterest(
                couponCalculationService.calculateInterest(coupon, bondModel)
        );
    }

    public CouponModel dispatchCoupon(final BondModel bondModel, final Integer quantityPerYear) {
        if (!needTryFetchFromDatabase(bondModel)) {
            return fetchCouponsFromTinkoffApi(bondModel, quantityPerYear);
        }

        return Optional.ofNullable(fetchCouponsFromDatabase(bondModel, quantityPerYear))
                .filter(databaseCoupon -> CollectionUtils.isNotEmpty(databaseCoupon.getCouponData()))
                .orElse(fetchCouponsFromTinkoffApi(bondModel, quantityPerYear));
    }

    private CouponModel fetchCouponsFromDatabase(final BondModel bondModel, final Integer quantityPerYear) {
        return tinkoffCouponApiUseCase.getCoupon(bondModel, quantityPerYear);
    }

    private CouponModel fetchCouponsFromTinkoffApi(final BondModel bondModel, final Integer quantityPerYear) {
        return tinkoffCouponApiUseCase.getCoupon(bondModel, quantityPerYear);
    }

    private boolean needTryFetchFromDatabase(final BondModel bondModel) {
        return BooleanUtils.isTrue(bondModel.getIsFixedCoupon());
    }
}
