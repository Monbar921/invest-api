package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffCouponMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponApiUseCase;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;

import java.util.List;
import java.util.Objects;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_UID;

@Component
@RequiredArgsConstructor
public class TinkoffCouponApiUseCaseImpl implements TinkoffCouponApiUseCase {
    private final TinkoffCouponMapper tinkoffCouponMapper;
    private final InstrumentsGrpcRateLimitedWrapper instrumentsGrpcRateLimitedWrapper;

    @Override
    public CouponModel getCoupon(final BondModel bondModel) {
        if (bondModel == null) {
            return null;
        }

        final List<CouponDataModel> couponsData = fetchCouponData(bondModel.getUid());

        return tinkoffCouponMapper.toModel(bondModel, couponsData);
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
                .map(tinkoffCouponMapper::toCouponDataModel)
                .toList();
    }
}
