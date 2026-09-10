package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffCouponApiMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponApiUseCase;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;

import java.util.List;
import java.util.Objects;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_UID;

@Service
@RequiredArgsConstructor
public class TinkoffCouponApiUseCaseImpl implements TinkoffCouponApiUseCase {
    private final TinkoffCouponApiMapper tinkoffCouponApiMapper;

    private final InstrumentsGrpcRateLimitedWrapper instrumentsGrpcRateLimitedWrapper;

    @Override
    public List<CouponDataModel> getCouponData(final String uid) {
        if (StringUtils.isEmpty(uid)) {
            throw new GeneralUnprocessableEntityException(EMPTY_UID, "Provide uid for getting coupon info");
        }

        return fetchCouponData(uid);
    }

    private List<CouponDataModel> fetchCouponData(final String uid) {
        final GetBondCouponsRequest request = GetBondCouponsRequest.newBuilder()
                .setInstrumentId(uid)
                .build();

        final GetBondCouponsResponse response = instrumentsGrpcRateLimitedWrapper.getBondCoupons(request);

        return response.getEventsList()
                .stream()
                .filter(Objects::nonNull)
                .map(tinkoffCouponApiMapper::toCouponDataModel)
                .toList();
    }
}
