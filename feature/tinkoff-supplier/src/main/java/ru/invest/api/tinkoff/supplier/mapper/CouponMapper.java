package ru.invest.api.tinkoff.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Coupon;

import java.util.List;

@Mapper(uses = {MoneyMapper.class, DateTimeMapper.class})
public interface CouponMapper {

    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "quantityPerYear", source = "couponQuantityPerYear")
    @Mapping(target = "couponData", source = "couponDtoList")
    @Mapping(target = "interest", ignore = true)
    CouponModel toModel(BondModel bond, List<CouponDataModel> couponDtoList, Integer couponQuantityPerYear);

    @Mapping(target = "price", source = "payOneBond")
    @Mapping(target = "paymentDate", source = "couponDate")
    CouponDataModel toCouponDataModel(Coupon coupon);
}
