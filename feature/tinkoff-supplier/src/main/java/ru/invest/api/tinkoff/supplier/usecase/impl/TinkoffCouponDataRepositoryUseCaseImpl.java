package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.CouponData;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.repository.CouponDataRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffCouponDataMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponDataRepositoryUseCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TinkoffCouponDataRepositoryUseCaseImpl implements TinkoffCouponDataRepositoryUseCase {
    private final CouponDataRepository couponDataRepository;

    private final TinkoffCouponDataMapper tinkoffCouponDataMapper;

    @Override
    @Transactional
    public Map<String, List<CouponDataModel>> saveCouponData(final Map<String, List<CouponDataModel>> couponDataBatch) {
        if (MapUtils.isEmpty(couponDataBatch)) {
            return Collections.emptyMap();
        }

        final List<CouponData> savedEntities = couponDataRepository.saveAll(
                tinkoffCouponDataMapper.toEntity(couponDataBatch)
        );

        return savedEntities
                .stream()
                .filter(Objects::nonNull)
                .map(tinkoffCouponDataMapper::toModel)
                .collect(Collectors.groupingBy(CouponDataModel::getTicker));
    }
}
