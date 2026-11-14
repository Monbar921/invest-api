package ru.invest.api.news.supplier.screener;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.usecase.BondScreenerEnricher;
import ru.invest.api.news.supplier.model.OfzYieldCurve;
import ru.invest.api.news.supplier.usecase.MoexBondDataUseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import static ru.invest.api.tinkoff.supplier.predicates.BondModelCurrencyPredicates.RUBBLE_CURRENCY_PREDICATE;

/**
 * Спред к ОФЗ (G-спред): доходность к погашению минус доходность кривой ОФЗ на срок, равный дюрации облигации.
 * Только рублёвые облигации: кривая - рублёвых ОФЗ. Нет доходности, дюрации или кривой - спреда нет.
 */
@Service
@RequiredArgsConstructor
public class OfzSpreadEnricher implements BondScreenerEnricher {
    private static final int SCALE = 2;

    private final MoexBondDataUseCase moexBondDataUseCase;

    @Override
    public void enrich(final Collection<BondModel> bonds, final boolean detailed) {
        // без подходящих облигаций биржу не спрашиваем
        if (bonds.stream().filter(Objects::nonNull).noneMatch(this::isCandidate)) {
            return;
        }
        final Optional<OfzYieldCurve> curve = moexBondDataUseCase.getOfzCurve();
        if (curve.isEmpty()) {
            return;
        }

        bonds.stream()
                .filter(Objects::nonNull)
                .filter(this::isCandidate)
                .forEach(bond -> curve.get().yieldAt(bond.getCoupon().getDurationMacaulay().doubleValue())
                        .ifPresent(curveYield -> bond
                                .setOfzCurveYield(curveYield.setScale(SCALE, RoundingMode.HALF_UP))
                                .setSpreadToOfz(bond.getCoupon().getYieldToMaturity().subtract(curveYield)
                                        .setScale(SCALE, RoundingMode.HALF_UP))));
    }

    private boolean isCandidate(final BondModel bond) {
        final CouponModel coupon = bond.getCoupon();
        return coupon != null && coupon.getYieldToMaturity() != null && coupon.getDurationMacaulay() != null
                && coupon.getDurationMacaulay().compareTo(BigDecimal.ZERO) > 0
                && RUBBLE_CURRENCY_PREDICATE.test(bond);
    }
}
