package ru.invest.api.tinkoff.supplier.mapper;

import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mapper(uses = {MoneyMapper.class, DateTimeMapper.class})
public abstract class CouponEntityMapper {

    public abstract CouponDataModel toCouponDataModel(Coupon coupon);

    public CouponModel toModel(final BondModel bond, final List<Coupon> coupons) {
        if (CollectionUtils.isEmpty(coupons)) {
            return null;
        }

        final List<CouponDataModel> couponData = coupons
                .stream()
                .filter(Objects::nonNull)
                .map(this::toCouponDataModel)
                .toList();

        return toCouponModel(bond, couponData);
    }

    public List<Coupon> toEntity(CouponModel couponModel, Bond bond) {
        return null;
    }

    @Mapping(target = "quantityPerYear", source = "bond.coupon.quantityPerYear")
    @Mapping(target = "isFixed", source = "bond.coupon.isFixed")
    @Mapping(target = "interest", source = "bond.coupon.interest")
    protected abstract CouponModel toCouponModel(BondModel bond, List<CouponDataModel> couponData);

    @ObjectFactory
    protected CouponModel objectFactory(final BondModel bond) {
        return Optional.ofNullable(bond)
                .map(BondModel::getCoupon)
                .orElseGet(CouponModel::new);
    }
}
