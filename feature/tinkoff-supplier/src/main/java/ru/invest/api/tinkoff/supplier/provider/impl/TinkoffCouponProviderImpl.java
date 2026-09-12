package ru.invest.api.tinkoff.supplier.provider.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffCouponApiMapper;
import ru.invest.api.tinkoff.supplier.provider.TinkoffCouponProvider;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;

import java.util.List;
import java.util.Objects;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_TICKER;
import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_UID;

@Service
@RequiredArgsConstructor
public class TinkoffCouponProviderImpl implements TinkoffCouponProvider {
    private final TinkoffCouponApiMapper tinkoffCouponApiMapper;

    private final InstrumentsGrpcRateLimitedWrapper instrumentsGrpcRateLimitedWrapper;

    @Override
    public List<CouponDataModel> getCouponData(final String uid, final String ticker, final AuditModel audit) {
        if (StringUtils.isEmpty(uid)) {
            throw new GeneralUnprocessableEntityException(EMPTY_UID, "Provide uid for getting coupon info");
        }
        if (StringUtils.isEmpty(ticker)) {
            throw new GeneralUnprocessableEntityException(EMPTY_TICKER, "Provide ticker for getting coupon info");
        }

        return fetchCouponData(uid, ticker, audit);
    }

    private List<CouponDataModel> fetchCouponData(final String uid, final String ticker, final AuditModel audit) {
        final GetBondCouponsRequest request = GetBondCouponsRequest.newBuilder()
                .setInstrumentId(uid)
                .build();

        final GetBondCouponsResponse response = instrumentsGrpcRateLimitedWrapper.getBondCoupons(request);

        return response.getEventsList()
                .stream()
                .filter(Objects::nonNull)
                .map(coupon -> tinkoffCouponApiMapper.toCouponDataModel(coupon, ticker, audit))
                .toList();
    }
}
