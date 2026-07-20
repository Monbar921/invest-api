package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.tinkoff.supplier.mapper.CouponMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponDatabaseUseCase;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TinkoffCouponDatabaseUseCaseImpl implements TinkoffCouponDatabaseUseCase {
    private static final String EMPTY_TICKER_MESSAGE = "Bond ticker can not be empty";
    private static final String EMPTY_UID_MESSAGE = "Coupon uid can not be empty";
    private static final String BOND_NOT_FOUND_MESSAGE = "Bond not found for uid ";

    private final CouponRepository couponRepository;
    private final BondRepository bondRepository;
    private final CouponMapper couponMapper;

    @Override
    public CouponModel getCoupon(final BondModel bondModel) {
        final String ticker = Optional.ofNullable(bondModel)
                .map(BondModel::getTicker)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.EMPTY_TICKER, EMPTY_TICKER_MESSAGE));

        final List<Coupon> coupons = couponRepository.findByTicker(ticker);

        if (CollectionUtils.isEmpty(coupons)) {
            return null;
        }

        return couponMapper.toModelFromEntities(bondModel, coupons);
    }

    @Override
    @Transactional
    public CouponModel saveCoupon(final CouponModel couponModel) {
        final String uid = Optional.ofNullable(couponModel)
                .map(CouponModel::getUid)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new GeneralUnprocessableEntityException(ExceptionErrorCode.EMPTY_UID, EMPTY_UID_MESSAGE));

        final Bond bond = bondRepository.findByUid(uid)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.BOND_NOT_FOUND, BOND_NOT_FOUND_MESSAGE + uid));

        couponRepository.deleteByBond_Uid(uid);
        couponRepository.saveAll(couponMapper.toEntities(couponModel, bond));

        return couponModel;
    }
}
