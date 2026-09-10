package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.CouponData;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.tinkoff.supplier.mapper.CouponDataMapper;
import ru.invest.api.tinkoff.supplier.mapper.CouponModelMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponRepositoryUseCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TinkoffCouponRepositoryUseCaseImpl implements TinkoffCouponRepositoryUseCase {
    private static final String EMPTY_TICKER_MESSAGE = "Bond ticker can not be empty";
    private static final String EMPTY_UID_MESSAGE = "Coupon uid can not be empty";
    private static final String BOND_NOT_FOUND_MESSAGE = "Bond not found for uid %s";

    private final CouponRepository couponRepository;
    private final BondRepository bondRepository;

    private final CouponModelMapper couponModelMapper;
    private final CouponDataMapper couponDataMapper;

    @Override
    @Transactional(readOnly = true)
    public CouponModel getCoupon(final BondModel bondModel) {
        final String ticker = Optional.ofNullable(bondModel)
                .map(BondModel::getTicker)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.EMPTY_TICKER, EMPTY_TICKER_MESSAGE));

        final List<CouponData> couponData = couponRepository.findByTicker(ticker);

        return couponModelMapper.toModel(bondModel, couponData);
    }

    @Override
    @Transactional
    public List<CouponDataModel> saveCouponData(final List<CouponDataModel> couponDataModel) {
        final String uid = Optional.ofNullable(couponDataModel)
                .map(CouponModel::getUid)
                .filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new GeneralUnprocessableEntityException(ExceptionErrorCode.EMPTY_UID, EMPTY_UID_MESSAGE));

        final Bond bond = bondRepository.findByUid(uid)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.BOND_NOT_FOUND, BOND_NOT_FOUND_MESSAGE.formatted(uid)));

        final List<CouponData> entities = couponDataMapper.toEntity(couponModel, bond);
        couponRepository.saveAll(entities);

        return couponModel;
    }

    @Override
    @Transactional
    public Map<String, List<CouponDataModel>> saveCouponData(final Map<String, List<CouponDataModel>> couponDataBatch) {
        if (MapUtils.isEmpty(couponDataBatch)) {
            return Collections.emptyMap();
        }

        final List<CouponData> savedEntities = couponRepository.saveAll(
                couponDataMapper.toEntity(couponDataBatch)
        );

        return savedEntities
                .stream()
                .filter(Objects::nonNull)
                .map(couponDataMapper::toModel)
                .collect(Collectors.groupingBy(CouponDataModel::getTicker));
    }
}
