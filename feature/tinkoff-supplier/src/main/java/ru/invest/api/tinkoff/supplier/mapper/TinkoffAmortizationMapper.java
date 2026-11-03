package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.entity.Amortization;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.MoneyModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mapper(uses = AuditMapper.class)
@SuppressWarnings("checkstyle:AbstractClassName")
public abstract class TinkoffAmortizationMapper {
    @Setter(onMethod_ = @Autowired)
    private TinkoffMoneyApiMapper tinkoffMoneyApiMapper;

    @Mapping(target = "logged", ignore = true)
    @Mapping(target = "price", source = "entity", qualifiedByName = "toMoneyModel")
    public abstract AmortizationModel toModel(Amortization entity);

    public abstract List<AmortizationModel> toModel(List<Amortization> entities);

    /**
     * Заменяет график амортизации облигации новым: старые выплаты удаляются через orphanRemoval.
     */
    public void replace(final Bond bond, final List<AmortizationModel> amortizations) {
        final List<Amortization> entities = Optional.ofNullable(bond.getAmortizations())
                .orElseGet(ArrayList::new);
        entities.clear();

        amortizations.stream()
                .filter(Objects::nonNull)
                .map(amortization -> toEntity(amortization, bond))
                .forEach(entities::add);

        bond.setAmortizations(entities);
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "bond", source = "bond")
    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "price", source = "amortization.price.quantity")
    @Mapping(target = "currency", source = "amortization.price.currency")
    @Mapping(target = "paymentDate", source = "amortization.paymentDate")
    @Mapping(target = "created", source = "amortization.logged")
    protected abstract Amortization toEntity(AmortizationModel amortization, Bond bond);

    @Named("toMoneyModel")
    protected MoneyModel toMoneyModel(final Amortization entity) {
        if (entity == null) {
            return null;
        }

        return tinkoffMoneyApiMapper.toModel(entity.getCurrency(), entity.getPrice());
    }
}
