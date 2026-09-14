
package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.CouponRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mapper(uses = TinkoffCouponDataMapper.class)
public abstract class TinkoffCouponMapper {
    private static final String BOND_NOT_FOUND_MESSAGE = "Bond not found for ticker %s";

    @Setter
    private AuditMapper auditMapper;
    @Setter
    private TinkoffCouponDataMapper tinkoffCouponDataMapper;
    @Setter(onMethod_ = @Autowired)
    private CouponRepository couponRepository;
    @Setter(onMethod_ = @Autowired)
    private BondRepository bondRepository;

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

    public List<Coupon> toEntity(final Map<String, List<CouponDataModel>> couponDataModelMap) {
        if (MapUtils.isEmpty(couponDataModelMap)) {
            return Collections.emptyList();
        }

        final List<Coupon> existingCoupons = couponRepository.findByUidIn(couponDataModelMap.keySet());
        final Map<String, Coupon> existingCouponsMap = existingCoupons
                .stream()
                .filter(Objects::nonNull)
                .filter(bond -> StringUtils.isNotBlank(bond.getTicker()))
                .collect(Collectors.toMap(Coupon::getUid, Function.identity()));

        final List<Coupon> newCoupons = couponDataModelMap
                .entrySet()
                .stream()
                .filter(entry -> !existingCouponsMap.containsKey(entry.getKey()))
                .map(entry -> toEntity(entry.getKey(), entry.getValue()))
                .toList();

        couponDataModelMap
                .entrySet()
                .stream()
                .filter(entry -> existingCouponsMap.containsKey(entry.getKey()))
                .forEach(entry -> toEntity(entry.getValue(), existingCouponsMap.get(entry.getKey())));

        if (CollectionUtils.isNotEmpty(newCoupons)) {
            existingCoupons.addAll(newCoupons);
        }
        return existingCoupons;
    }

    private Coupon toEntity(final String uid, final List<CouponDataModel> couponDataModels) {
        final Bond bond = bondRepository.findByUid(uid)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.BOND_NOT_FOUND, BOND_NOT_FOUND_MESSAGE.formatted(uid)));

        final Coupon coupon = toEntity(bond, null);
        return toEntity(couponDataModels, coupon);
    }

    private Coupon toEntity(final List<CouponDataModel> couponDataModels, final Coupon coupon) {
        if (CollectionUtils.isEmpty(couponDataModels)) {
            return coupon;
        }

        coupon.setCouponData(
                tinkoffCouponDataMapper.toEntity(couponDataModels, coupon)
        );

        return coupon;
    }

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
