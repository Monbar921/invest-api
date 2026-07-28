package ru.invest.api.tinkoff.supplier.mapper;

import org.apache.commons.collections4.MapUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.PriceModel;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Mapper(uses = {DateTimeMapper.class})
public abstract class TinkoffBondEntityMapper {
    private static final String SYSTEM_AUDIT_USER = "tinkoff-bond-scheduler";

    public Bond toEntity(final BondModel protoBond, final Bond existing) {
        final Bond target = Optional.ofNullable(existing)
                .orElseGet(Bond::new);
        updateEntity(target, protoBond);
        return target;
    }

    @Mapping(target = "price", source = "price")
    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "isin", source = "bond.isin")
    @Mapping(target = "name", source = "bond.name")
    @Mapping(target = "sector", source = "bond.sector")
    @Mapping(target = "riskLevel", source = "bond.riskLevel")
    @Mapping(target = "coupon", source = "bond", qualifiedByName = "toInitialCoupon")
    @Mapping(target = "maturityDate", source = "bond.maturityDate")
    public abstract BondModel toModel(Bond bond, PriceModel price);

    @Mapping(target = "price", source = "bond")
    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "isin", source = "bond.isin")
    @Mapping(target = "name", source = "bond.name")
    @Mapping(target = "sector", source = "bond.sector")
    @Mapping(target = "riskLevel", source = "bond.riskLevel")
    @Mapping(target = "coupon", source = "bond", qualifiedByName = "toInitialCoupon")
    @Mapping(target = "maturityDate", source = "bond.maturityDate")
    public abstract BondModel toModel(Bond bond);

    public List<BondModel> enrichBonds(final Map<String, BondModel> bonds,
                                       final Map<String, PriceModel> bondPrices) {
        if (MapUtils.isEmpty(bonds)) {
            return Collections.emptyList();
        }

        final Map<String, PriceModel> prices = Optional.ofNullable(bondPrices)
                .orElse(Collections.emptyMap());

        return bonds.entrySet()
                .stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.getValue() != null)
                .peek(entry -> entry.getValue().setPrice(prices.get(entry.getKey())))
                .map(Map.Entry::getValue)
                .toList();
    }

    @Mapping(target = "currency", source = "price.current.currency")
    @Mapping(target = "couponQuantityPerYear", source = "coupon.quantityPerYear")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "nominalCurrency", source = "price.nominal.currency")
    @Mapping(target = "nominalPrice", source = "price.nominal.quantity")
    @Mapping(target = "isFixedCoupon", source = "protoBond.coupon.isFixed")
    protected abstract void updateEntity(@MappingTarget Bond entity, BondModel protoBond);

    @AfterMapping
    protected void stampAudit(@MappingTarget final Bond entity) {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (entity.getId() == null) {
            entity.setCreated(new Audit().setCommittedBy(SYSTEM_AUDIT_USER).setCommittedAt(now));
        } else {
            entity.setUpdated(new Audit().setCommittedBy(SYSTEM_AUDIT_USER).setCommittedAt(now));
        }
    }

    @Named("toInitialCoupon")
    protected CouponModel toInitialCoupon(final Bond bond) {
        if (bond == null) {
            return null;
        }

        return new CouponModel()
                .setQuantityPerYear(Optional.ofNullable(bond.getCouponQuantityPerYear()).orElse(0))
                .setIsFixed(bond.getIsFixedCoupon());
    }
}
