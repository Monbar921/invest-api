package ru.invest.api.stock.supplier.mapper;

import lombok.Setter;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.usecase.CurrencyUseCase;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.math.BigDecimal;

@Mapper(uses = {MoneyMapper.class})
public abstract class PriceMapper {
    @Setter(onMethod_ = @Autowired)
    private MoneyMapper moneyMapper;
    @Setter(onMethod_ = @Autowired)
    private CurrencyUseCase currencyUseCase;

    @Mapping(target = "uid", source = "uid")
    @Mapping(target = "nominal", source = "nominal")
    @Mapping(target = "current", ignore = true)
    public abstract PriceModel toBondPriceModel(String uid, MoneyValue nominal, BigDecimal current, String currency);

    @AfterMapping
    protected void afterMapping(@MappingTarget final PriceModel priceModel, final String uid, final MoneyValue nominal
            , final BigDecimal current, final String currency) {
        priceModel.setCurrent(moneyMapper.toModel(currency, current));
        if (quantityNotNullComparator(priceModel) && currencyNotNullComparator(priceModel) && !currencyComparator(priceModel)) {
            final CurrencyModel currencyModel = currencyUseCase.calculateAmount(
                    priceModel.getCurrent().getCurrency(), priceModel.getNominal().getCurrency(), priceModel.getCurrent().getQuantity());
            priceModel.getCurrent()
                    .setCurrency(currencyModel.getTarget())
                    .setQuantity(currencyModel.getRate());
        }
    }

    protected boolean quantityNotNullComparator(final PriceModel priceModel) {
        return priceModel.getNominal() != null && priceModel.getNominal().getQuantity() != null
                && priceModel.getCurrent() != null && priceModel.getCurrent().getQuantity() != null;
    }

    protected boolean currencyNotNullComparator(final PriceModel priceModel) {
        return priceModel.getNominal() != null && priceModel.getNominal().getCurrency() != null
                && priceModel.getCurrent() != null && priceModel.getCurrent().getCurrency() != null;
    }

    protected boolean currencyComparator(final PriceModel priceModel) {
        return priceModel.getCurrent().getCurrency().equalsIgnoreCase(priceModel.getNominal().getCurrency());
    }
}
