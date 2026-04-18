package ru.invest.api.stock.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.model.MoneyModel;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

@Mapper
public interface MoneyMapper {

    @Mapping(target = "quantity", source = "units")
    MoneyModel toModel(MoneyValue moneyValue);


    MoneyModel toModel(String name);
}
