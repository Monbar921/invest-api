package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.usecase.CouponRefreshUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponApiUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponDatabaseUseCase;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponRefreshUseCaseImpl implements CouponRefreshUseCase {
    private final BondRepository bondRepository;
    private final TinkoffCouponApiUseCase tinkoffCouponApiUseCase;
    private final TinkoffCouponDatabaseUseCase tinkoffCouponDatabaseUseCase;

    @Override
    public void refreshCoupons() {
        final List<Bond> bonds = bondRepository.findBondsRequiringCouponRefresh();

        if (CollectionUtils.isEmpty(bonds)) {
            return;
        }

        bonds.forEach(this::refreshCoupon);
    }

    private void refreshCoupon(final Bond bond) {
        try {
            final BondModel probe = new BondModel()
                    .setUid(bond.getUid())
                    .setTicker(bond.getTicker())
                    .setCoupon(new CouponModel().setQuantityPerYear(Optional.ofNullable(bond.getCouponQuantityPerYear()).orElse(0)));

            final CouponModel fetched = tinkoffCouponApiUseCase.getCoupon(probe);

            if (fetched != null) {
                tinkoffCouponDatabaseUseCase.saveCoupon(fetched);
            }
        } catch (final Exception e) {
            log.warn("Failed to refresh coupons for bond uid={} ticker={}", bond.getUid(), bond.getTicker(), e);
        }
    }
}
