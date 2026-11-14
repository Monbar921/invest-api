package ru.invest.api.common.usecase.impl;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.rating.CreditRatingModel;
import ru.invest.api.common.model.screener.LiquidityModel;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Значения облигации для фильтров и сортировок скринера; null - значения нет.
 */
final class BondScreenerValues {
    private BondScreenerValues() {
    }

    static BigDecimal duration(final BondModel bond) {
        return Optional.ofNullable(bond.getCoupon()).map(CouponModel::getDurationMacaulay).orElse(null);
    }

    static BigDecimal yieldToMaturity(final BondModel bond) {
        return Optional.ofNullable(bond.getCoupon()).map(CouponModel::getYieldToMaturity).orElse(null);
    }

    static Integer ratingScore(final BondModel bond) {
        return Optional.ofNullable(bond.getCreditRating()).map(CreditRatingModel::getScore).orElse(null);
    }

    static BigDecimal avgDailyTurnoverRub(final BondModel bond) {
        return Optional.ofNullable(bond.getLiquidity()).map(LiquidityModel::getAvgDailyTurnoverRub).orElse(null);
    }
}
