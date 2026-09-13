package ru.invest.api.common.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.common.model.MoneyModel;

import java.math.BigDecimal;

@Mapper
public abstract class MoneyMapper {

    public abstract MoneyModel toModel(String currency, BigDecimal quantity);
}
