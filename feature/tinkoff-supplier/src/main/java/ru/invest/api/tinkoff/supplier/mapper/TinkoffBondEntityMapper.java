package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.apache.commons.collections4.MapUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.mapper.BigDecimalMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.math.BigDecimal;
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

    @Setter(onMethod_ = @Autowired)
    private BigDecimalMapper bigDecimalMapper;

    @Setter(onMethod_ = @Autowired)
    private MoneyMapper moneyMapper;

    public Bond toEntity(final ru.tinkoff.piapi.contract.v1.Bond protoBond, final Bond existing) {
        final Bond target = existing != null ? existing : new ru.invest.api.common.entity.Bond();
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

    public List<BondModel> toModelFromEntities(final Map<String, Bond> bonds,
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

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "nominalCurrency", source = "nominal.currency")
    @Mapping(target = "nominalPrice", source = "nominal", qualifiedByName = "toNominalPrice")
    @Mapping(target = "isFixedCoupon", expression = "java(!protoBond.getFloatingCouponFlag())")
    protected abstract void updateEntity(@MappingTarget Bond entity, ru.tinkoff.piapi.contract.v1.Bond protoBond);

    @AfterMapping
    protected void stampAudit(@MappingTarget final Bond entity) {
        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if (entity.getId() == null) {
            entity.setCreated(new Audit().setCommittedBy(SYSTEM_AUDIT_USER).setCommittedAt(now));
        } else {
            entity.setUpdated(new Audit().setCommittedBy(SYSTEM_AUDIT_USER).setCommittedAt(now));
        }
    }

    @Named("toNominalPrice")
    protected BigDecimal toNominalPrice(final MoneyValue nominal) {
        if (nominal == null) {
            return null;
        }

        return bigDecimalMapper.fromBaseAndNanoFloatParts(nominal.getUnits(), nominal.getNano());
    }


    @Named("toInitialPrice")
    protected PriceModel toInitialPrice(final Bond bond) {
        if (bond == null) {
            return null;
        }

        return new PriceModel()
                .setCurrent(moneyMapper.toModel(bond.getCurrency(), null))
                .setNominal(moneyMapper.toModel(bond.getNominalCurrency(), bond.getNominalPrice()));
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
