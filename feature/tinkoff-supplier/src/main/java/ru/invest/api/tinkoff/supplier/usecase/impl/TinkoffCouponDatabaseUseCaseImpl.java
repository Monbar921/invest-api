package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponDatabaseUseCase;

@Component
@RequiredArgsConstructor
public class TinkoffCouponDatabaseUseCaseImpl implements TinkoffCouponDatabaseUseCase {
    private static final String ERROR_MESSAGE = "Bond with uid=%s not found";

    private final BondRepository bondRepository;

    @Override
    public CouponModel getCoupon(final BondModel bondModel) {
        return bondRepository.findByUid(bondModel.getUid())
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.BOND_NOT_FOUND,
                        ERROR_MESSAGE.formatted(bondModel.getUid())));
    }

    @Override
    public CouponModel saveCoupon(final CouponModel couponModel) {
        return null;
    }
}
