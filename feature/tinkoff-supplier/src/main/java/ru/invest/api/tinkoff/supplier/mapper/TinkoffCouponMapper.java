
package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;

@Mapper(uses = TinkoffCouponDataMapper.class)
public abstract class TinkoffCouponMapper {
    @Setter
    private AuditMapper auditMapper;

    @Mapping(target = "couponData", source = "couponData")
    public abstract CouponModel toModel(Coupon coupon);

    @Mapping(target = "created", ignore = true)
    @Mapping(target = "updated", ignore = true)
    @Mapping(target = "interest", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "couponData", ignore = true)
    @Mapping(target = "isFixed", source = "bondModel.coupon.isFixed")
    @Mapping(target = "quantityPerYear", source = "bondModel.coupon.quantityPerYear")
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
}
