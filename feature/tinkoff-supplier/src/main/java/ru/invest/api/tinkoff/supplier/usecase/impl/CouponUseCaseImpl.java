package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.tinkoff.supplier.mapper.CouponMapper;
import ru.invest.api.tinkoff.supplier.service.CouponCalculationService;
import ru.invest.api.tinkoff.supplier.usecase.CouponUseCase;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;

import java.util.List;
import java.util.Objects;

import static ru.invest.api.common.constants.CacheConstants.COUPON_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.COUPON_CACHE_NAME;
import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_UID;

@Component
@RequiredArgsConstructor
public class CouponUseCaseImpl implements CouponUseCase {
    private final CouponMapper couponMapper;
    private final InstrumentsGrpcRateLimitedWrapper instrumentsGrpcRateLimitedWrapper;
    private final CouponCalculationService couponCalculationService;

    @Override
    @Cacheable(value = COUPON_CACHE_NAME, cacheManager = COUPON_CACHE_MANAGER, key = "#bond.uid")
    public CouponModel getCoupons(final BondModel bondModel, final Bond bond) {
        if (bond == null) {
            return null;
        }

        final List<CouponDataModel> couponDataList = fetchCouponData(bond.getUid());
        final CouponModel couponModel = couponMapper.toModel(bond, couponDataList);
        couponModel.setInterest(couponCalculationService.calculateInterest(couponModel, bondModel));
        return couponModel;
    }

    private List<CouponDataModel> fetchCouponData(final String uid) {
        if (StringUtils.isEmpty(uid)) {
            throw new GeneralUnprocessableEntityException(EMPTY_UID, "Provide bond uid for getting coupon info");
        }

        final GetBondCouponsRequest request = GetBondCouponsRequest.newBuilder()
                .setInstrumentId(uid)
                .build();

        final GetBondCouponsResponse response = instrumentsGrpcRateLimitedWrapper.getBondCoupons(request);

        return response.getEventsList()
                .stream()
                .filter(Objects::nonNull)
                .map(couponMapper::toCouponDataModel)
                .toList();
    }
}
