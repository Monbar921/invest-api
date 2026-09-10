
package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.entity.CouponData;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mapper
public abstract class CouponMapper {
    @Setter
    private CouponDataMapper couponDataMapper;
    @Setter
    private AuditMapper auditMapper;

    public CouponModel toModel(final BondModel bond, final List<CouponData> coupons) {
        if (CollectionUtils.isEmpty(coupons)) {
            return null;
        }

        final List<CouponDataModel> couponData = coupons
                .stream()
                .filter(Objects::nonNull)
                .map(couponDataMapper::toModel)
                .toList();

        return toCouponModel(bond, couponData);
    }

    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "quantityPerYear", ignore = true)
    @Mapping(target = "nominalInterest", ignore = true)
    @Mapping(target = "isFixedCoupon", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentInterest", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "couponData", ignore = true)
    @Mapping(target = "bond", source = "bond")
    @Mapping(target = "ticker", source = "bond")
    public abstract Coupon toEntity(Bond bond);

    @ObjectFactory
    protected Coupon objectFactory(final Bond bond) {
        if(bond.getCoupon() == null) {
            return new Coupon()
                    .setCreated(auditMapper.toEntity());
        }

        return bond.getCoupon();
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
