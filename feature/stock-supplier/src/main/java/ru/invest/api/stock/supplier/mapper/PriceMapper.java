package ru.invest.api.stock.supplier.mapper;

import lombok.Setter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.mapper.BigDecimalMapper;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(uses = {MoneyMapper.class})
public abstract class PriceMapper {
    private static final BigDecimal PERCENTAGE = BigDecimal.valueOf(100.0);
    private static final int SCALE = 10;

    @Setter(onMethod_ = @Autowired)
    private BigDecimalMapper bigDecimalMapper;


    @Mapping(target = "uid", source = "lastPrice.instrumentUid")
    @Mapping(target = "nominal", source = "nominal")
    @Mapping(target = "current", ignore = true)
    @Mapping(target = "percentagePrice", ignore = true)
    public abstract PriceModel toBondPriceModel(LastPrice lastPrice, MoneyValue nominal);

    @AfterMapping
    protected void afterMapping(@MappingTarget final PriceModel priceModel, final LastPrice lastPrice) {

        final BigDecimal currentPercentage = bigDecimalMapper.fromBaseAndNanoFloatParts(lastPrice.getPrice().getUnits(),
                lastPrice.getPrice().getNano());
        priceModel.setPercentagePrice(currentPercentage);
        priceModel.setCurrent(
                getCurrent(priceModel.getNominal(), currentPercentage)
        );
    }

    protected MoneyModel getCurrent(final MoneyModel nominal, final BigDecimal currentPercentage) {
        if (ObjectUtils.anyNull(nominal, currentPercentage) || StringUtils.isBlank(nominal.getCurrency())
                || nominal.getQuantity() == null) {
            return null;
        }

        final BigDecimal currentValue = currentPercentage.divide(PERCENTAGE, SCALE, RoundingMode.CEILING).multiply(nominal.getQuantity());

        return new MoneyModel()
                .setQuantity(currentValue)
                .setCurrency(nominal.getCurrency());
    }
}
