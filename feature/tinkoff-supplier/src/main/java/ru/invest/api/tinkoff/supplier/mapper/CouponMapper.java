package ru.invest.api.tinkoff.supplier.mapper;

import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.MoneyModel;
import ru.tinkoff.piapi.contract.v1.Coupon;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mapper(uses = {MoneyMapper.class, DateTimeMapper.class})
public interface CouponMapper {

    @Mapping(target = "uid", source = "bond.uid")
    @Mapping(target = "quantityPerYear", source = "bond.coupon.quantityPerYear")
    @Mapping(target = "couponData", source = "couponDtoList")
    @Mapping(target = "interest", ignore = true)
    @Mapping(target = "isFixed", ignore = true)
    CouponModel toModel(BondModel bond, List<CouponDataModel> couponDtoList);

    @Mapping(target = "price", source = "payOneBond")
    @Mapping(target = "paymentDate", source = "couponDate")
    CouponDataModel toCouponDataModel(Coupon coupon);

    @ObjectFactory
    default CouponModel objectFactory(final BondModel bond) {
        return Optional.ofNullable(bond)
                .map(BondModel::getCoupon)
                .orElseGet(CouponModel::new);
    }

    default CouponModel toModelFromEntities(final BondModel bond, final List<ru.invest.api.common.entity.Coupon> coupons) {
        final CouponModel couponModel = objectFactory(bond)
                .setUid(Optional.ofNullable(bond).map(BondModel::getUid).orElse(null))
                .setCouponData(
                        Optional.ofNullable(coupons)
                                .orElse(Collections.emptyList())
                                .stream()
                                .filter(Objects::nonNull)
                                .map(this::toCouponDataModel)
                                .toList()
                );

        return couponModel;
    }

    default CouponDataModel toCouponDataModel(final ru.invest.api.common.entity.Coupon coupon) {
        if (coupon == null) {
            return null;
        }

        return new CouponDataModel()
                .setPrice(new MoneyModel()
                        .setCurrency(coupon.getCurrency())
                        .setQuantity(coupon.getPrice()))
                .setFixDate(coupon.getFixDate())
                .setPaymentDate(coupon.getPaymentDate());
    }

    default List<ru.invest.api.common.entity.Coupon> toEntities(final CouponModel couponModel, final ru.invest.api.common.entity.Bond bond) {
        if (couponModel == null || CollectionUtils.isEmpty(couponModel.getCouponData())) {
            return Collections.emptyList();
        }

        return couponModel.getCouponData()
                .stream()
                .filter(Objects::nonNull)
                .map(data -> toEntity(data, bond, couponModel.getQuantityPerYear()))
                .toList();
    }

    default ru.invest.api.common.entity.Coupon toEntity(final CouponDataModel data, final ru.invest.api.common.entity.Bond bond,
                                                         final Integer quantityPerYear) {
        final ru.invest.api.common.entity.Coupon coupon = new ru.invest.api.common.entity.Coupon();
        coupon.setBond(bond);
        coupon.setQuantityPerYear(quantityPerYear);
        coupon.setPrice(Optional.ofNullable(data.getPrice()).map(MoneyModel::getQuantity).orElse(null));
        coupon.setCurrency(Optional.ofNullable(data.getPrice()).map(MoneyModel::getCurrency).orElse(null));
        coupon.setFixDate(data.getFixDate());
        coupon.setPaymentDate(data.getPaymentDate());

        return coupon;
    }
}
