package ru.invest.api.tinkoff.supplier.mapper;

import org.mapstruct.Mapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TinkoffBondModelMapper {
    default void enrichByCoupons(final BondModel bondModel, final List<CouponDataModel> couponDataModels) {
        if (bondModel == null) {
            return;
        }

        final CouponModel couponModel = Optional.ofNullable(bondModel.getCoupon())
                .orElseGet(CouponModel::new);

        couponModel.setCouponData(couponDataModels);
        bondModel.setCoupon(couponModel);
    }
}
