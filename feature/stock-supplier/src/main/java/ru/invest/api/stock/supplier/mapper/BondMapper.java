package ru.invest.api.stock.supplier.mapper;

import org.apache.commons.collections4.MapUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.parameters.BondParameters;
import ru.tinkoff.piapi.contract.v1.Bond;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Mapper(uses = {PriceMapper.class, CouponMapper.class})
public abstract class BondMapper {

    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "isin", source = "bond.isin")
    @Mapping(target = "name", source = "bond.name")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "coupon", source = "bond")
    public abstract BondModel toModel(Bond bond, PriceModel price);

    public List<BondModel> toModel(final Map<String, Bond> bonds, final Map<String, PriceModel> bondPrices
            , final BondParameters bondParameters) {
        if (MapUtils.isEmpty(bonds)) {
            return Collections.emptyList();
        }

        final Map<String, Bond> limitedBonds = bonds.entrySet().stream()
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));

        final Map<String, PriceModel> prices = Optional.ofNullable(bondPrices)
                .orElse(Collections.emptyMap());

        return limitedBonds.entrySet()
                .stream()
                .filter(Objects::nonNull)
                .map(entry -> toModel(entry.getValue(), prices.get(entry.getKey())))
                .toList();
    }
}
