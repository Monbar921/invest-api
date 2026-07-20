package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.mapper.BigDecimalMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper(uses = {DateTimeMapper.class})
public abstract class BondPersistenceMapper {
    private static final String SYSTEM_AUDIT_USER = "tinkoff-bond-scheduler";

    @Setter(onMethod_ = @Autowired)
    private BigDecimalMapper bigDecimalMapper;

    public ru.invest.api.common.entity.Bond toEntity(final Bond protoBond, final ru.invest.api.common.entity.Bond existing) {
        final ru.invest.api.common.entity.Bond target = existing != null ? existing : new ru.invest.api.common.entity.Bond();
        updateEntity(protoBond, target);
        return target;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "nominalCurrency", source = "nominal.currency")
    @Mapping(target = "nominalPrice", source = "nominal", qualifiedByName = "toNominalPrice")
    @Mapping(target = "isFixedCoupon", expression = "java(!protoBond.getFloatingCouponFlag())")
    protected abstract void updateEntity(Bond protoBond, @MappingTarget ru.invest.api.common.entity.Bond entity);

    @AfterMapping
    protected void stampAudit(@MappingTarget final ru.invest.api.common.entity.Bond entity) {
        final LocalDateTime now = LocalDateTime.now();

        if (entity.getCreated() == null) {
            entity.setCreated(new Audit().setCommittedBy(SYSTEM_AUDIT_USER).setCommittedAt(now));
        }

        entity.setUpdated(new Audit().setCommittedBy(SYSTEM_AUDIT_USER).setCommittedAt(now));
    }

    @Named("toNominalPrice")
    protected BigDecimal toNominalPrice(final MoneyValue nominal) {
        if (nominal == null) {
            return null;
        }

        return bigDecimalMapper.fromBaseAndNanoFloatParts(nominal.getUnits(), nominal.getNano());
    }
}
