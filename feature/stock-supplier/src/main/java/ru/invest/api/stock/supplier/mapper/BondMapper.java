package ru.invest.api.stock.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.Bond;

import java.util.List;
import java.util.Map;

@Mapper(uses = {PriceMapper.class, CouponMapper.class})
public interface BondMapper {

    @Mapping(target = "coupon", source = "bond")
    BondModel toModel(Bond bond);

    List<BondModel> toModel(Map<String, Bond> bonds, Map<String, PriceModel> bondPrices);
}
