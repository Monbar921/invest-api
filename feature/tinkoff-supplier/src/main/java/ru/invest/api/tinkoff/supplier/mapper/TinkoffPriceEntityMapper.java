package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Mapper(uses = {AuditMapper.class})
@SuppressWarnings("checkstyle:AbstractClassName")
public abstract class TinkoffPriceEntityMapper {
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final int PERCENTAGE_SCALE = 4;

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

    // синхронизация цен меняет только текущую цену, номинал и привязка к облигации остаются как есть
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "price", source = "current.quantity")
    @Mapping(target = "currency", source = "current.currency")
    public abstract void updateCurrentPrice(PriceModel priceModel, @MappingTarget Price price);

    @Mapping(target = "logged", ignore = true)
    @Mapping(target = "percentagePrice", source = "entity", qualifiedByName = "toPercentagePrice")
    // цена в рублях зависит от курса ЦБ и считается при загрузке облигаций в кэш
    @Mapping(target = "currentInRub", ignore = true)
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

    /**
     * Цена в процентах от номинала. Отдельно не хранится: текущая цена при синхронизации считается как процент x номинал,
     * поэтому процент восстанавливается обратно - с точностью, которую даёт округление цены до копеек.
     */
    @Named("toPercentagePrice")
    protected BigDecimal toPercentagePrice(final Price entity) {
        if (entity == null || ObjectUtils.anyNull(entity.getPrice(), entity.getNominalPrice())
                || entity.getNominalPrice().signum() == 0
                || !StringUtils.equalsIgnoreCase(entity.getCurrency(), entity.getNominalCurrency())) {
            return null;
        }

        return entity.getPrice()
                .multiply(PERCENT)
                .divide(entity.getNominalPrice(), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
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
    protected void afterCurrentPriceMapping(@MappingTarget final Price price, final PriceModel priceModel) {
        price.setUpdated(auditMapper.toEntity(priceModel.getLogged()));
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
