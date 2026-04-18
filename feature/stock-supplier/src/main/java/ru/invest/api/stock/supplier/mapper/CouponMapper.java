package ru.invest.api.stock.supplier.mapper;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Coupon;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

import java.util.List;
import java.util.Objects;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_UID;

@Mapper(uses = {MoneyMapper.class})
public abstract class CouponMapper {
    @Setter(onMethod_ = {@Autowired})
    protected SyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> instrumentsServiceBlockingStub;

    public CouponModel toModel(final Bond bond) {
        if (bond == null) {
            return null;
        }

        final List<CouponDataModel> couponDtoList = getCouponData(bond.getUid());
        return toModel(bond, couponDtoList);
    }

    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "quantityPerYear", source = "bond.couponQuantityPerYear")
    @Mapping(target = "couponData", source = "couponDtoList")
    protected abstract CouponModel toModel(Bond bond, List<CouponDataModel> couponDtoList);

    @Mapping(target = "price", source = "payOneBond")
    @Mapping(target = "paymentDate", source = "couponDate")
    protected abstract CouponDataModel toCouponDataModel(Coupon coupon);

    protected List<CouponDataModel> getCouponData(final String uid) {
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
                .map(this::toCouponDataModel)
                .toList();
    }
}
