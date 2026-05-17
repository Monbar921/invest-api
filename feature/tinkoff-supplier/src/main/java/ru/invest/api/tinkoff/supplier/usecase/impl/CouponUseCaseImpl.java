package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.tinkoff.supplier.mapper.CouponMapper;
import ru.invest.api.tinkoff.supplier.usecase.CouponUseCase;
import ru.invest.api.tinkoff.supplier.wrapper.impl.InstrumentsGrpcRateLimitedWrapperImpl;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;

import java.util.List;
import java.util.Objects;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_UID;

@Component
@RequiredArgsConstructor
public class CouponUseCaseImpl implements CouponUseCase {
    private final CouponMapper couponMapper;
    private final InstrumentsGrpcRateLimitedWrapperImpl instrumentsGrpcRateLimitedWrapper;

    @Override
    public CouponModel getCoupons(final BondModel bondModel, final Bond bond) {
        if (bond == null) {
            return null;
        }

        final List<CouponDataModel> couponDtoList = getCouponData(bond.getUid());
        return couponMapper.toModel(bond, bondModel, couponDtoList);
    }

    private List<CouponDataModel> getCouponData(final String uid) {
        if (StringUtils.isEmpty(uid)) {
            throw new GeneralUnprocessableEntityException(EMPTY_UID, "Provide bond uid for getting coupon info");
        }

        final GetBondCouponsRequest request = GetBondCouponsRequest.newBuilder()
                .setInstrumentId(uid)
                .build();

        final GetBondCouponsResponse response = instrumentsGrpcRateLimitedWrapper
                .getBondCoupons(request);

        return response.getEventsList()
                .stream()
                .filter(Objects::nonNull)
                .map(couponMapper::toCouponDataModel)
                .toList();
    }
}
