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
    private static final BigDecimal NANO_MULTIPLIER = BigDecimal.valueOf(1_000_000_000);

    @Setter(onMethod_ = {@Autowired})
    private BigDecimalMapper bigDecimalMapper;

    @Mapping(target = "quantity", source = "moneyValue", qualifiedByName = "toQuantity")
    public abstract MoneyModel toModel(MoneyValue moneyValue);

    public abstract MoneyModel toModel(String currency, BigDecimal quantity);


    public MoneyValue toMoneyValue(final BigDecimal quantity, final String currency) {
        if (quantity == null) {
            return null;
        }

        final long units = quantity.longValue();
        final int nano = quantity.subtract(BigDecimal.valueOf(units))
                .multiply(NANO_MULTIPLIER)
                .intValue();

        return MoneyValue.newBuilder()
                .setUnits(units)
                .setNano(nano)
                .setCurrency(currency)
                .build();
    }

    @Named("toQuantity")
    protected BigDecimal toQuantity(final MoneyValue moneyValue) {
        if (moneyValue == null) {
            return null;
        }

        return bigDecimalMapper.fromBaseAndNanoFloatParts(moneyValue.getUnits(), moneyValue.getNano());
    }
}
