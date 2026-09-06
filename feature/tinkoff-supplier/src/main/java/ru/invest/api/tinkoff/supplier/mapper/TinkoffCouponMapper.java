package ru.invest.api.tinkoff.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.CouponDataModel;
import ru.tinkoff.piapi.contract.v1.Coupon;

@Mapper(uses = {MoneyMapper.class, DateTimeMapper.class})
public interface TinkoffCouponMapper {
    @Mapping(target = "price", source = "payOneBond")
    @Mapping(target = "paymentDate", source = "couponDate")
    CouponDataModel toCouponDataModel(Coupon coupon);
}
