package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.CouponData;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.tinkoff.supplier.mapper.CouponDataMapper;
import ru.invest.api.tinkoff.supplier.mapper.CouponMapper;
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

    private final CouponRepository couponRepository;

    private final CouponMapper couponMapper;
    private final CouponDataMapper couponDataMapper;

    @Override
    @Transactional(readOnly = true)
    public CouponModel getCoupon(final BondModel bondModel) {
        final String ticker = Optional.ofNullable(bondModel)
                .map(BondModel::getTicker)
                .orElseThrow(() -> new GeneralUnprocessableEntityException(ExceptionErrorCode.EMPTY_TICKER, EMPTY_TICKER_MESSAGE));

        final List<CouponData> couponData = couponRepository.findByTicker(ticker);

        return couponMapper.toModel(bondModel, couponData);
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
