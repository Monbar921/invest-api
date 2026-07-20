package ru.invest.api.tinkoff.supplier.mapper;

import org.apache.commons.collections4.MapUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.Bond;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Mapper(uses = {PriceMapper.class, DateTimeMapper.class})
public interface BondMapper {

    @Mapping(target = "price", source = "price")
    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "isin", source = "bond.isin")
    @Mapping(target = "name", source = "bond.name")
    @Mapping(target = "sector", source = "bond.sector")
    @Mapping(target = "riskLevel", source = "bond.riskLevel")
    @Mapping(target = "coupon", source = "bond", qualifiedByName = "toInitialCoupon")
    @Mapping(target = "maturityDate", source = "bond.maturityDate")
    BondModel toModel(Bond bond, PriceModel price);

    @Mapping(target = "price", ignore = true)
    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "isin", source = "bond.isin")
    @Mapping(target = "name", source = "bond.name")
    @Mapping(target = "sector", source = "bond.sector")
    @Mapping(target = "riskLevel", source = "bond.riskLevel")
    @Mapping(target = "coupon", source = "bond", qualifiedByName = "toInitialCoupon")
    @Mapping(target = "maturityDate", source = "bond.maturityDate")
    BondModel toModel(Bond bond);

    @Mapping(target = "price", source = "price")
    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "isin", source = "bond.isin")
    @Mapping(target = "name", source = "bond.name")
    @Mapping(target = "sector", source = "bond.sector")
    @Mapping(target = "riskLevel", source = "bond.riskLevel")
    @Mapping(target = "coupon", source = "bond", qualifiedByName = "toInitialCouponFromEntity")
    @Mapping(target = "maturityDate", source = "bond.maturityDate")
    BondModel toModel(ru.invest.api.common.entity.Bond bond, PriceModel price);

    @Named("toInitialCoupon")
    default CouponModel toInitialCoupon(final Bond bond) {
        if (bond == null) {
            return null;
        }

        return new CouponModel()
                .setQuantityPerYear(bond.getCouponQuantityPerYear())
                .setIsFixed(!bond.getFloatingCouponFlag());
    }

    @Named("toInitialCouponFromEntity")
    default CouponModel toInitialCouponFromEntity(final ru.invest.api.common.entity.Bond bond) {
        if (bond == null) {
            return null;
        }

        return new CouponModel()
                .setQuantityPerYear(Optional.ofNullable(bond.getCouponQuantityPerYear()).orElse(0))
                .setIsFixed(bond.getIsFixedCoupon());
    }

    default List<BondModel> toModelFromEntities(final Map<String, ru.invest.api.common.entity.Bond> bonds,
                                                final Map<String, PriceModel> bondPrices) {
        if (MapUtils.isEmpty(bonds)) {
            return Collections.emptyList();
        }

        final Map<String, PriceModel> prices = Optional.ofNullable(bondPrices)
                .orElse(Collections.emptyMap());

        return bonds.entrySet()
                .stream()
                .filter(Objects::nonNull)
                .map(entry -> toModel(entry.getValue(), prices.get(entry.getKey())))
                .toList();
    }
}
