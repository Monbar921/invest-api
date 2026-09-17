package ru.invest.api.tinkoff.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.tinkoff.piapi.contract.v1.Bond;

@Mapper(uses = {TinkoffPriceApiMapper.class, DateTimeMapper.class})
public interface TinkoffBondApiMapper {

    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "logged", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "ticker", source = "ticker")
    @Mapping(target = "uid", source = "uid")
    @Mapping(target = "isin", source = "isin")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "sector", source = "sector")
    @Mapping(target = "riskLevel", source = "riskLevel")
    @Mapping(target = "coupon", source = "bond", qualifiedByName = "toInitialCoupon")
    @Mapping(target = "maturityDate", source = "maturityDate")
    @Mapping(target = "price", source = "nominal")
    BondModel toModel(Bond bond);

    @Named("toInitialCoupon")
    default CouponModel toInitialCoupon(final Bond bond) {
        if (bond == null) {
            return null;
        }

        return new CouponModel()
                .setQuantityPerYear(bond.getCouponQuantityPerYear())
                .setIsFixed(!bond.getFloatingCouponFlag());
    }
}
