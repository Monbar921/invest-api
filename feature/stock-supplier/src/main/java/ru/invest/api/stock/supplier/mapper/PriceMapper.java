package ru.invest.api.stock.supplier.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.math.BigDecimal;

@Mapper(uses = {MoneyMapper.class})
public interface PriceMapper {
    @Mapping(target = "uid", source = "uid")
    @Mapping(target = "nominal", source = "nominal")
    @Mapping(target = "current", source = "current")
    @Mapping(target = "currency", source = "currency")
    PriceModel toBondPriceModel(String uid, MoneyValue nominal, BigDecimal current, String currency);
}
