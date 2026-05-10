package ru.invest.api.stock.supplier.mapper;

import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.Coupon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mapper(uses = {MoneyMapper.class, DateTimeMapper.class})
public abstract class CouponMapper {
    @Setter(onMethod_ = {@Autowired})
    private DateTimeMapper dateTimeMapper;

    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "quantityPerYear", source = "bond.couponQuantityPerYear")
    @Mapping(target = "couponData", source = "couponDtoList")
    @Mapping(target = "interest", ignore = true)
    public abstract CouponModel toModel(Bond bond, BondModel bondModel, List<CouponDataModel> couponDtoList);

    @Mapping(target = "price", source = "payOneBond")
    @Mapping(target = "paymentDate", source = "couponDate")
    public abstract CouponDataModel toCouponDataModel(Coupon coupon);

    @AfterMapping
    protected void afterMapping(@MappingTarget final CouponModel couponModel,  final BondModel bondModel, final List<CouponDataModel> couponData) {
        if (CollectionUtils.isEmpty(couponData)) {
            return;
        }

        final LocalDateTime now = dateTimeMapper.getNow();
        final LocalDateTime oneYearLater = now.plusDays(365);

        final BigDecimal paymentSum = couponData.stream()
                .filter(coupon -> coupon.getPaymentDate() != null)
                .filter(coupon -> coupon.getPaymentDate().isAfter(now) &&
                        coupon.getPaymentDate().isBefore(oneYearLater))
                .map(CouponDataModel::getPrice)
                .filter(Objects::nonNull)
                .map(MoneyModel::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        couponModel.setInterest(getInterest(paymentSum, bondModel.getPrice()));
    }

    protected BigDecimal getInterest(final BigDecimal paymentSum, final PriceModel currentPrice) {
        if (paymentSum == null) {
            return null;
        }
        return Optional.ofNullable(currentPrice)
                .map(PriceModel::getCurrent)
                .map(current -> paymentSum.divide(current, RoundingMode.FLOOR))
                .orElse(null);
    }
}
