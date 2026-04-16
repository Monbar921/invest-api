package ru.invest.api.stock.supplier.mapper;

import lombok.Setter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.usecase.CurrencyUseCase;
import ru.tinkoff.piapi.contract.v1.Bond;

import java.math.BigDecimal;

@Mapper
public abstract class PriceMapper {
    @Setter(onMethod_ = @Autowired)
    private CurrencyUseCase currencyUseCase;

    @Mapping(target = "nominalPercentage", source = "nominalPercentage")
    public abstract PriceModel toModel(Bond bond, BigDecimal nominalPercentage);
}
