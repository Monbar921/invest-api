package ru.invest.api.tinkoff.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.tinkoff.piapi.contract.v1.Coupon;

@Mapper(uses = {MoneyMapper.class, DateTimeMapper.class})
public interface TinkoffCouponApiMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ticker", source = "ticker")
    @Mapping(target = "fetchedAudit", source = "audit")
    @Mapping(target = "price", source = "coupon.payOneBond")
    @Mapping(target = "paymentDate", source = "coupon.couponDate")
    CouponDataModel toCouponDataModel(Coupon coupon, String ticker, AuditModel audit);
}
