package ru.invest.api.stock.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.Bond;

import java.math.BigDecimal;

@Mapper(uses = {MoneyValueMapper.class})
public interface PriceMapper {
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "nominal", source = "bond.nominal", qualifiedByName = "toNominalMoney")
    @Mapping(target = "nominalPercentage", source = "nominalPercentage")
    PriceModel toModel(Bond bond, BigDecimal nominalPercentage);
}
