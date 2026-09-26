package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.apache.commons.lang3.ObjectUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Price;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;

import java.util.Optional;

@Mapper(uses = {AuditMapper.class})
public abstract class TinkoffPriceEntityMapper {
    @Setter(onMethod_ = @Autowired)
    private AuditMapper auditMapper;
    @Setter(onMethod_ = @Autowired)
    private TinkoffMoneyApiMapper tinkoffMoneyApiMapper;

    // текущую цену (price/currency) заполняет синхронизация цен, при сохранении облигации пишем только номинал
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "bond", source = "bond")
    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "nominalPrice", source = "bondModel.price.nominal.quantity")
    @Mapping(target = "nominalCurrency", source = "bondModel.price.nominal.currency")
    public abstract Price toEntity(Bond bond, BondModel bondModel);

    @Mapping(target = "logged", ignore = true)
    @Mapping(target = "percentagePrice", ignore = true)
    @Mapping(target = "nominal", source = "entity", qualifiedByName = "toNominal")
    @Mapping(target = "current", source = "entity", qualifiedByName = "toCurrent")
    public abstract PriceModel toModel(Price entity);

    @Named("toNominal")
    protected MoneyModel toNominal(final Price entity) {
        if (entity == null || ObjectUtils.allNull(entity.getNominalPrice(), entity.getNominalCurrency())) {
            return null;
        }

        return tinkoffMoneyApiMapper.toModel(entity.getNominalCurrency(), entity.getNominalPrice());
    }

    @Named("toCurrent")
    protected MoneyModel toCurrent(final Price entity) {
        if (entity == null || ObjectUtils.allNull(entity.getPrice(), entity.getCurrency())) {
            return null;
        }

        return tinkoffMoneyApiMapper.toModel(entity.getCurrency(), entity.getPrice());
    }

    @ObjectFactory
    protected Price objectFactory(final Bond bond) {
        return Optional.ofNullable(bond.getPrice())
                .orElseGet(Price::new);
    }

    @AfterMapping
    protected void afterMapping(@MappingTarget final Price price, final BondModel bondModel) {
        final Audit audit = auditMapper.toEntity(bondModel.getLogged());

        if (price.getId() == null) {
            price.setCreated(audit);
        } else {
            price.setUpdated(audit);
        }
    }
}
