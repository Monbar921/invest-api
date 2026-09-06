package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.annotation.Active;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.usecase.CouponSyncUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponApiUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponRepositoryUseCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
@Active
public class TinkoffCouponSyncUseCaseImpl implements CouponSyncUseCase {
    private final TinkoffCouponApiUseCase tinkoffCouponApiUseCase;
    private final TinkoffCouponRepositoryUseCase tinkoffCouponRepositoryUseCase;
    private final BondRepository bondRepository;

    @Override
    public void syncAll() {
        final List<BondModel> bonds = getBondsToUpdate();

        if (CollectionUtils.isEmpty(bonds)) {
            return;
        }

        bonds.forEach(this::refreshCoupon);
    }

    private void refreshCoupon(final BondModel bond) {
        final CouponModel fetched = tinkoffCouponApiUseCase.getCoupon(bond);

        if (fetched != null) {
            tinkoffCouponRepositoryUseCase.saveCoupon(fetched);
        }
    }

    private List<BondModel> getBondsToUpdate() {
        final Map<String, BondModel> bonds = tinkoffBondCacheUseCase.getBonds();

        if (MapUtils.isEmpty(bonds)) {
            return Collections.emptyList();
        }

        return bonds
                .entrySet()
                .stream()
                .filter(Objects::nonNull)
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .filter(bond -> bond.getCoupon() == null || !bond.getCoupon().getIsFixed()
                        || CollectionUtils.isEmpty(bond.getCoupon().getCouponData()))
                .toList();
    }
}
