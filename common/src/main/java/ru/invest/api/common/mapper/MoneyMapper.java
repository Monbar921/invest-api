package ru.invest.api.common.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.common.model.MoneyModel;

import java.math.BigDecimal;

@Mapper
public interface MoneyMapper {

    MoneyModel toModel(String currency, BigDecimal quantity);
}
