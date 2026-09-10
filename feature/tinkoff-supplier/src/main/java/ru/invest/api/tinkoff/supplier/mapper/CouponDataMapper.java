package ru.invest.api.tinkoff.supplier.mapper;

import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.CouponData;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.CouponRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Mapper(uses = {MoneyMapper.class, DateTimeMapper.class})
public abstract class CouponDataMapper {
    @Setter(onMethod_ = @Autowired)
    private MoneyMapper moneyMapper;
    @Setter(onMethod_ = @Autowired)
    private CouponRepository couponRepository;
    @Setter(onMethod_ = @Autowired)
    private BondRepository bondRepository;

    @Mapping(target = "price", source = "couponData", qualifiedByName = "toMoneyModel")
    public abstract CouponDataModel toModel(CouponData couponData);

    public List<CouponData> toEntity(final List<CouponDataModel> couponModel) {
        if (CollectionUtils.isEmpty(couponModel)) {
            return Collections.emptyList();
        }
    }

    public List<CouponData> toEntity(final Map<String, List<CouponDataModel>> couponModelMap) {
        if (MapUtils.isEmpty(couponModelMap)) {
            return Collections.emptyList();
        }

        final List<CouponData> existingEntities = couponRepository.findByTickerIn(couponModelMap.keySet());
        if (CollectionUtils.isNotEmpty(existingEntities)) {
            existingEntities.clear();
        }

        final Map<String, Bond> bonds = bondRepository.findByTickerIn(couponModelMap.keySet())
                .stream()
                .filter(Objects::nonNull)
                .filter(bond -> StringUtils.isNotBlank(bond.getTicker()))
                .collect(Collectors.toMap(Bond::getTicker, Function.identity()));

        final List<CouponData> newEntities = couponModelMap
                .entrySet()
                .stream()
                .flatMap(entryModel -> toEntity(entryModel.getKey(), entryModel.getValue(), bonds).stream())
                .toList();

        if (CollectionUtils.isNotEmpty(newEntities)) {
            existingEntities.addAll(newEntities);
        }
        return existingEntities;
    }

    protected List<CouponData> toEntity(final String ticker, final List<CouponDataModel> couponDataModels, final Map<String, Bond> bonds) {
        if (CollectionUtils.isEmpty(couponDataModels)) {
            return new ArrayList<>();
        }

        return couponDataModels
                .stream()
                .filter(Objects::nonNull)
                .map(couponDataModel -> toEntity(couponDataModel, bonds.get(ticker)))
                .collect(Collectors.toList());
    }

    @Mapping(target = "id", ignore = true)
    protected abstract CouponData toEntity(CouponDataModel couponData, Bond bond);

    @Named("toMoneyModel")
    protected MoneyModel toMoneyModel(final CouponData coupon) {
        if (coupon == null) {
            return null;
        }

        return moneyMapper.toModel(coupon.getCurrency(), coupon.getPrice());
    }
}
