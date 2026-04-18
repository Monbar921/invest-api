package ru.invest.api.stock.supplier.mapper;

import org.apache.commons.collections4.MapUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.Bond;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Mapper(uses = {PriceMapper.class, CouponMapper.class})
public abstract class BondMapper {

    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "isin", source = "bond.isin")
    @Mapping(target = "name", source = "bond.name")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "coupon", source = "bond")
    public abstract BondModel toModel(Bond bond, PriceModel price);

    public List<BondModel> toModel(final Map<String, Bond> bonds, final Map<String, PriceModel> bondPrices) {
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
