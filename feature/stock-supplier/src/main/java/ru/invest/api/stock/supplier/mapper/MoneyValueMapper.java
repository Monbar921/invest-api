package ru.invest.api.stock.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.invest.api.common.model.MoneyModel;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

@Mapper
public interface MoneyValueMapper {

    @Named("toNominalMoney")
    @Mapping(target = "quantity", source = "units")
    MoneyModel toNominalMoneyModel(MoneyValue moneyValue);
}
