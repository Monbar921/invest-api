package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.usecase.CurrencyUseCase;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mapper(uses = {MoneyMapper.class, DateTimeMapper.class})
public abstract class CouponMapper {
    private static final int SCALE = 10;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100.0);

    @Setter(onMethod_ = {@Autowired})
    private DateTimeMapper dateTimeMapper;
    @Setter(onMethod_ = @Autowired)
    private CurrencyUseCase currencyUseCase;

    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "quantityPerYear", source = "bond.couponQuantityPerYear")
    @Mapping(target = "couponData", source = "couponDtoList")
    @Mapping(target = "interest", ignore = true)
    public abstract CouponModel toModel(Bond bond, BondModel bondModel, List<CouponDataModel> couponDtoList);

    @Mapping(target = "price", source = "payOneBond")
    @Mapping(target = "paymentDate", source = "couponDate")
    public abstract CouponDataModel toCouponDataModel(Coupon coupon);

    @AfterMapping
    protected void afterMapping(@MappingTarget final CouponModel couponModel, final BondModel bondModel, final List<CouponDataModel> couponData) {
        if (CollectionUtils.isEmpty(couponData)) {
            return;
        }

        final String bondPriceCurrency = Optional.ofNullable(bondModel.getPrice())
                .map(PriceModel::getCurrent)
                .map(MoneyModel::getCurrency)
                .orElse(null);

        if (StringUtils.isBlank(bondPriceCurrency)) {
            return;
        }

        final List<CouponDataModel> oneYearCoupons = getOneYearCoupons(couponData, couponModel.getQuantityPerYear());

        final BigDecimal paymentSum = oneYearCoupons
                .stream()
                .map(CouponDataModel::getPrice)
                .filter(Objects::nonNull)
                .filter(price -> StringUtils.isNotBlank(price.getCurrency()) && price.getQuantity() != null)
                .map(coupon -> fixQuantityByCurrency(coupon, bondPriceCurrency))
                .map(MoneyModel::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        couponModel.setInterest(getInterest(paymentSum, bondModel.getPrice()));
    }

    protected List<CouponDataModel> getOneYearCoupons(final List<CouponDataModel> couponData, final int payments) {
        if (CollectionUtils.isEmpty(couponData) || payments == 0) {
            return couponData;
        }

        final LocalDateTime now = dateTimeMapper.getNow();
        return couponData
                .stream()
                .filter(Objects::nonNull)
                .filter(coupon -> coupon.getPaymentDate() != null)
                .filter(coupon -> coupon.getPaymentDate().isAfter(now))
                .sorted(Comparator.comparing(CouponDataModel::getPaymentDate, Comparator.naturalOrder()))
                .limit(payments)
                .toList();
    }

    protected MoneyModel fixQuantityByCurrency(final MoneyModel coupon, final String bondPriceCurrency) {
        if (!bondPriceCurrency.equalsIgnoreCase(coupon.getCurrency())) {
            final CurrencyModel calculatedCurrency = currencyUseCase.calculateAmount(
                    coupon.getCurrency(), bondPriceCurrency, coupon.getQuantity());

            return new MoneyModel()
                    .setQuantity(calculatedCurrency.getRate())
                    .setCurrency(calculatedCurrency.getTarget());
        }

        return coupon;
    }

    protected BigDecimal getInterest(final BigDecimal paymentSum, final PriceModel currentPrice) {
        if (ObjectUtils.anyNull(paymentSum, currentPrice) || currentPrice.getCurrent().getQuantity() == null) {
            return null;
        }

        return paymentSum.multiply(PERCENT).divide(currentPrice.getCurrent().getQuantity(), SCALE, RoundingMode.FLOOR);
    }
}
