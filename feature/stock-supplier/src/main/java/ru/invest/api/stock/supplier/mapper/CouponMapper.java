package ru.invest.api.stock.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

@Mapper
public interface CouponMapper {

    @Mapping(target = "quantityPerYear", source = "couponQuantityPerYear")
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "currency", ignore = true)
    CouponModel toModel(Bond bond);
}
