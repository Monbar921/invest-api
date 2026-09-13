
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

@Mapper(uses = CouponDataMapper.class)
public abstract class CouponMapper {
    @Setter
    private CouponDataMapper couponDataMapper;
    @Setter
    private AuditMapper auditMapper;

    public abstract CouponModel toModel(Coupon coupon);

    @Mapping(target = "created", ignore = true)
    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "quantityPerYear", source = "bondModel.coupon.quantityPerYear")
    @Mapping(target = "nominalInterest", ignore = true)
    @Mapping(target = "isFixedCoupon", source = "bondModel.coupon.quantityPerYear")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "currentInterest", ignore = true)
    @Mapping(target = "couponData", ignore = true)
    @Mapping(target = "bond", source = "bond")
    @Mapping(target = "ticker", source = "bond.ticker")
    @Mapping(target = "uid", source = "bond.uid")
    public abstract Coupon toEntity(Bond bond, BondModel bondModel);

    @ObjectFactory
    protected Coupon objectFactory(final Bond bond, final BondModel bondModel) {
        if (bond.getCoupon() == null) {
            return new Coupon()
                    .setCreated(auditMapper.toEntity(bondModel.getLogged()));
        }

        return bond.getCoupon()
                .setUpdated(auditMapper.toEntity(bondModel.getLogged()));
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
