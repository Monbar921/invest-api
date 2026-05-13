package ru.invest.api.stock.supplier.mapper;

import lombok.Setter;
import org.apache.commons.collections4.MapUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.stock.supplier.usecase.CouponUseCase;
import ru.tinkoff.piapi.contract.v1.Bond;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static ru.invest.api.stock.supplier.constants.Constants.COUPON_EXECUTOR_SERVICE;

@Mapper(uses = {PriceMapper.class})
public abstract class BondMapper {
    private static final int SCALE = 10;
    private static final BigDecimal PERCENTAGE = BigDecimal.valueOf(100);

    @Setter(onMethod_ = {@Autowired})
    private CouponUseCase couponUseCase;
    @Setter(onMethod_ = {@Autowired, @Qualifier(COUPON_EXECUTOR_SERVICE)})
    private ExecutorService couponExecutorService;

    @Mapping(target = "valuePrice", source = "valuePrice")
    @Mapping(target = "percentagePrice", ignore = true)
    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "isin", source = "bond.isin")
    @Mapping(target = "name", source = "bond.name")
    @Mapping(target = "riskLevel", source = "bond.riskLevel")
    @Mapping(target = "coupon", source = "bond")
    public abstract BondModel toModel(Bond bond, PriceModel valuePrice);

    public List<BondModel> toModel(final Map<String, Bond> bonds, final Map<String, PriceModel> bondPrices) {
        if (MapUtils.isEmpty(bonds)) {
            return Collections.emptyList();
        }

        final Map<String, PriceModel> prices = Optional.ofNullable(bondPrices)
                .orElse(Collections.emptyMap());

        try {
            final List<CompletableFuture<BondModel>> futures = bonds.entrySet()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(entry -> CompletableFuture.supplyAsync(
                            () -> toModel(entry.getValue(), prices.get(entry.getKey())),
                            couponExecutorService
                    ))
                    .toList();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

        } finally {
            couponExecutorService.shutdown();
        }
    }

    @AfterMapping
    protected void afterMapping(final @MappingTarget BondModel bondModel, final Bond bond) {
        bondModel.setPercentagePrice(toPercentagePrice(bondModel));
        bondModel.setCoupon(couponUseCase.getCoupons(bondModel, bond));
    }

    protected PriceModel toPercentagePrice(final BondModel bondModel) {
        if (bondModel == null || bondModel.getValuePrice() == null) {
            return null;
        }

        final PriceModel valuePrice = bondModel.getValuePrice();

        final BigDecimal currentPrice = Optional.of(valuePrice)
                .filter(price -> price.getCurrent() != null && price.getCurrent().getQuantity() != null)
                .filter(price -> price.getNominal() != null && price.getNominal().getQuantity() != null)
                .map(price -> price.getCurrent().getQuantity()
                        .divide(PERCENTAGE, SCALE, RoundingMode.FLOOR)
                        .multiply(price.getNominal().getQuantity()))
                .orElse(null);

        final String currentCurrency = Optional.ofNullable(valuePrice.getCurrent())
                .map(MoneyModel::getCurrency)
                .orElse(null);

        return new PriceModel()
                .setUid(valuePrice.getUid())
                .setNominal(valuePrice.getNominal())
                .setCurrent(new MoneyModel()
                        .setCurrency(currentCurrency)
                        .setQuantity(currentPrice)
                );
    }
}
