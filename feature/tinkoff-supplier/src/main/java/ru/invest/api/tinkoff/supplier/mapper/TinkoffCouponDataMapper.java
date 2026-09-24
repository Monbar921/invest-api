package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.entity.CouponData;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.mapper.MoneyMapper;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.MoneyModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Mapper(uses = {MoneyMapper.class, DateTimeMapper.class})
public abstract class TinkoffCouponDataMapper {
    @Setter(onMethod_ = @Autowired)
    private TinkoffMoneyApiMapper tinkoffMoneyApiMapper;
    @Setter(onMethod_ = @Autowired)
    private AuditMapper auditMapper;

    @Mapping(target = "logged", ignore = true)
    @Mapping(target = "price", source = "couponData", qualifiedByName = "toMoneyModel")
    public abstract CouponDataModel toModel(CouponData couponData);

    public List<CouponData> toEntity(final List<CouponDataModel> couponDataModels, final Coupon coupon) {
        if (coupon == null || CollectionUtils.isEmpty(couponDataModels)) {
            return Collections.emptyList();
        }

        final List<CouponData> existingEntities = Optional.ofNullable(coupon.getCouponData())
                .orElseGet(ArrayList::new);
        if (CollectionUtils.isNotEmpty(existingEntities)) {
            existingEntities.clear();
        }

        final List<CouponData> newEntities = couponDataModels
                .stream()
                .map(couponDataModel -> toEntity(couponDataModel, coupon))
                .toList();

        if (CollectionUtils.isNotEmpty(newEntities)) {
            existingEntities.addAll(newEntities);
        }
        return existingEntities;
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ticker", source = "couponData.ticker")
    @Mapping(target = "uid", source = "couponData.uid")
    @Mapping(target = "price", source = "couponData.price.quantity")
    @Mapping(target = "currency", source = "couponData.price.currency")
    @Mapping(target = "coupon", source = "coupon")
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "updated", ignore = true)
    protected abstract CouponData toEntity(CouponDataModel couponData, Coupon coupon);

    @AfterMapping
    protected void afterMapping(@MappingTarget final CouponData couponData, final CouponDataModel model) {
        final Audit audit = auditMapper.toEntity(model.getLogged());
        if (couponData.getId() == null) {
            couponData.setCreated(audit);
        } else {
            couponData.setUpdated(audit);
        }
    }

    @Named("toMoneyModel")
    protected MoneyModel toMoneyModel(final CouponData coupon) {
        if (coupon == null) {
            return null;
        }

        return tinkoffMoneyApiMapper.toModel(coupon.getCurrency(), coupon.getPrice());
    }
}
