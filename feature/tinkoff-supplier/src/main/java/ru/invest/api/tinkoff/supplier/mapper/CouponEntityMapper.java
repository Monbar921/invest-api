package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.CouponData;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.MoneyModel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Mapper(uses = {MoneyMapper.class, DateTimeMapper.class})
public abstract class CouponEntityMapper {
    @Setter(onMethod_ = @Autowired)
    private MoneyMapper moneyMapper;

    @Mapping(target = "price", source = "couponData", qualifiedByName = "toMoneyModel")
    public abstract CouponDataModel toCouponDataModel(CouponData couponData);

    public CouponModel toModel(final BondModel bond, final List<CouponData> coupons) {
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

    public List<CouponData> toEntity(CouponModel couponModel, Bond bond) {
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

    @Named("toMoneyModel")
    protected MoneyModel toMoneyModel(final CouponData coupon) {
        if (coupon == null) {
            return null;
        }

        return moneyMapper.toModel(coupon.getCurrency(), coupon.getPrice());
    }
}
