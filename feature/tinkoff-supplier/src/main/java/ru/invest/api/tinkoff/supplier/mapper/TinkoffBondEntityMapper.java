package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.apache.commons.collections4.MapUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Mapper(uses = {DateTimeMapper.class})
public abstract class TinkoffBondEntityMapper {
    @Setter(onMethod_ = @Autowired)
    private AuditMapper auditMapper;
    @Setter(onMethod_ = @Autowired)
    private TinkoffCouponMapper tinkoffCouponMapper;

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "coupon", ignore = true)
    @Mapping(target = "ticker", source = "bondModel.ticker")
    @Mapping(target = "uid", source = "bondModel.uid")
    @Mapping(target = "isin", source = "bondModel.isin")
    @Mapping(target = "name", source = "bondModel.name")
    @Mapping(target = "sector", source = "bondModel.sector")
    @Mapping(target = "riskLevel", source = "bondModel.riskLevel")
    @Mapping(target = "maturityDate", source = "bondModel.maturityDate")
    @Mapping(target = "currency", source = "bondModel.currency")
    @Mapping(target = "nominalCurrency", source = "bondModel.price.nominal.currency")
    @Mapping(target = "nominalPrice", source = "bondModel.price.nominal.quantity")
    public abstract Bond toEntity(BondModel bondModel, Bond existing);

    @Mapping(target = "logged", ignore = true)
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "isin", source = "bond.isin")
    @Mapping(target = "name", source = "bond.name")
    @Mapping(target = "sector", source = "bond.sector")
    @Mapping(target = "riskLevel", source = "bond.riskLevel")
    @Mapping(target = "coupon", ignore = true)
    @Mapping(target = "maturityDate", source = "bond.maturityDate")
    public abstract BondModel toModel(Bond bond);
    public abstract List<BondModel> toModel(List<Bond> bonds);


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

    @ObjectFactory
    protected Bond objectFactory(final Bond existing) {

        return Optional.ofNullable(existing)
                .orElseGet(Bond::new);
    }

    @AfterMapping
    protected void afterMapping(@MappingTarget final Bond bond, final BondModel bondModel) {
        final Audit audit = auditMapper.toEntity(bondModel.getLogged());

        if (bond.getId() == null) {
            bond.setCreated(audit);
        } else {
            bond.setUpdated(audit);
        }

        bond.setCoupon(
                tinkoffCouponMapper.toEntity(bond, bondModel)
        );
    }
}
