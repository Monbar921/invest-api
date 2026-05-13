package ru.invest.api.common.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.common.model.CurrencyModel;

@Mapper
public interface CurrencyMapper {
    CurrencyModel clone(CurrencyModel currencyModel);
}
