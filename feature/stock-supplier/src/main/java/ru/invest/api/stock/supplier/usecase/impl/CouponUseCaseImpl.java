package ru.invest.api.stock.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.stock.supplier.mapper.CouponMapper;
import ru.invest.api.stock.supplier.usecase.CouponUseCase;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

import java.util.List;
import java.util.Objects;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_UID;

@Component
@RequiredArgsConstructor
public class CouponUseCaseImpl implements CouponUseCase {
    private final CouponMapper couponMapper;
    protected SyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> instrumentsServiceBlockingStub;

    @Override
    public CouponModel getCoupons(final Bond bond) {
        if (bond == null) {
            return null;
        }

        final List<CouponDataModel> couponDtoList = getCouponData(bond.getUid());
        return couponMapper.toModel(bond, couponDtoList);
    }

    private List<CouponDataModel> getCouponData(final String uid) {
        if (StringUtils.isEmpty(uid)) {
            throw new GeneralUnprocessableEntityException(EMPTY_UID, "Provide bond uid for getting coupon info");
        }

        final GetBondCouponsRequest request = GetBondCouponsRequest.newBuilder()
                .setInstrumentId(uid)
                .build();

        final GetBondCouponsResponse response = instrumentsServiceBlockingStub.getStub()
                .getBondCoupons(request);

        return response.getEventsList()
                .stream()
                .filter(Objects::nonNull)
                .map(couponMapper::toCouponDataModel)
                .toList();
    }
}
