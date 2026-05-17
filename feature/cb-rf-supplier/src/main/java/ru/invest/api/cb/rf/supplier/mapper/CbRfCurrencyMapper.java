package ru.invest.api.cb.rf.supplier.mapper;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.invest.api.cb.rf.supplier.model.CurrencyDto;
import ru.invest.api.cb.rf.supplier.model.CurrencyElementDto;
import ru.invest.api.common.model.CurrencyModel;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Mapper
public interface CbRfCurrencyMapper {
    @Mapping(target = "base", constant = "RUB")
    @Mapping(target = "target", source = "charCode", qualifiedByName = "toUpper")
    CurrencyModel toModel(CurrencyElementDto currencyDto);

    default List<CurrencyModel> toModel(final CurrencyDto currencyDto) {
        if (currencyDto == null || CollectionUtils.isEmpty(currencyDto.getCurrencies())) {
            return Collections.emptyList();
        }

        return currencyDto.getCurrencies()
                .stream()
                .filter(Objects::nonNull)
                .map(this::toModel)
                .toList();
    }

    @Named("toUpper")
    default String toUpper(final String source) {
        if (StringUtils.isNotBlank(source)) {
            return source.toUpperCase();
        }

        return null;
    }
}
