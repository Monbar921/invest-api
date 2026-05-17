package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.mapper.BigDecimalMapper;
import ru.invest.api.common.model.MoneyModel;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.math.BigDecimal;

@Mapper
public abstract class MoneyMapper {
    @Setter(onMethod_ = {@Autowired})
    private BigDecimalMapper bigDecimalMapper;

    @Mapping(target = "quantity", source = "moneyValue", qualifiedByName = "toQuantity")
    public abstract MoneyModel toModel(MoneyValue moneyValue);

    public abstract MoneyModel toModel(String currency, BigDecimal quantity);

    @Named("toQuantity")
    protected BigDecimal toQuantity(final MoneyValue moneyValue) {
        if (moneyValue == null) {
            return null;
        }

        return bigDecimalMapper.fromBaseAndNanoFloatParts(moneyValue.getUnits(), moneyValue.getNano());
    }
}
