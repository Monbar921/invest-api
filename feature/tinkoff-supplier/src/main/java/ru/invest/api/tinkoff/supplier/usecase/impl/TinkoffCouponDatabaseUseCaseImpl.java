package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.common.entity.Coupon;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponDatabaseUseCase;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TinkoffCouponDatabaseUseCaseImpl implements TinkoffCouponDatabaseUseCase {
    private static final String EMPTY_ERROR_MESSAGE = "Bond ticker can not be empty";

    private final CouponRepository couponRepository;

    @Override
    public CouponModel getCoupon(final BondModel bondModel) {
        final String ticker = Optional.ofNullable(bondModel)
                .map(BondModel::getTicker)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.EMPTY_TICKER, EMPTY_ERROR_MESSAGE));

        final List<Coupon> coupons = couponRepository.findByTicker(ticker);


    }

    @Override
    public CouponModel saveCoupon(final CouponModel couponModel) {
        return null;
    }
}
