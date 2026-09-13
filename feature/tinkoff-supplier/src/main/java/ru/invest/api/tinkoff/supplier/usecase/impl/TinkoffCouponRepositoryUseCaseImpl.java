package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.tinkoff.supplier.mapper.CouponMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponRepositoryUseCase;

@Service
@RequiredArgsConstructor
public class TinkoffCouponRepositoryUseCaseImpl implements TinkoffCouponRepositoryUseCase {
    private static final String EMPTY_UID_MESSAGE = "Bond uid can not be empty";
    private static final String COUPON_NOT_FOUND_MESSAGE = "Coupon with uid=%s is not found";

    private final CouponRepository couponRepository;

    private final CouponMapper couponMapper;

    @Transactional(readOnly = true)
    public CouponModel getCouponByUid(final String uid) {
        if (StringUtils.isBlank(uid)) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.EMPTY_TICKER, EMPTY_UID_MESSAGE);
        }

        return couponRepository.findByUid(uid)
                .map(couponMapper::toModel)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.COUPON_NOT_FOUND, COUPON_NOT_FOUND_MESSAGE.formatted(uid)));
    }
}
