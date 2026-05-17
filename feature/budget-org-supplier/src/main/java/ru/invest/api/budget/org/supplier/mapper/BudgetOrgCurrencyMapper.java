package ru.invest.api.budget.org.supplier.mapper;

import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import ru.invest.api.budget.org.supplier.model.CurrencyDto;
import ru.invest.api.common.model.CurrencyModel;

@Mapper
public interface BudgetOrgCurrencyMapper {
    @Mapping(target = "base", source = "baseCode", qualifiedByName = "toUpper")
    @Mapping(target = "target", source = "targetCode", qualifiedByName = "toUpper")
    @Mapping(target = "rate", source = "conversionRate")
    CurrencyModel toModel(CurrencyDto currencyDto);

    @Named("toUpper")
    default String toUpper(final String source) {
        if (StringUtils.isNotBlank(source)) {
            return source.toUpperCase();
        }

        return null;
    }
}
