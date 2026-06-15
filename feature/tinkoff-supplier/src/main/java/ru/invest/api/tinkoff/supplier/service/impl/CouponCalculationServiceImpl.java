package ru.invest.api.tinkoff.supplier.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.currency.service.usecase.CurrencyUseCase;
import ru.invest.api.tinkoff.supplier.service.CouponCalculationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CouponCalculationServiceImpl implements CouponCalculationService {
    private static final int SCALE = 10;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100.0);

    private final CurrencyUseCase currencyUseCase;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public BigDecimal calculateInterest(final CouponModel couponModel, final BondModel bondModel) {
        final List<CouponDataModel> couponData = couponModel.getCouponData();
        if (CollectionUtils.isEmpty(couponData)) {
            return null;
        }

        final String bondPriceCurrency = Optional.ofNullable(bondModel.getPrice())
                .map(PriceModel::getCurrent)
                .map(MoneyModel::getCurrency)
                .orElse(null);

        if (StringUtils.isBlank(bondPriceCurrency)) {
            return null;
        }

        final List<CouponDataModel> oneYearCoupons = getOneYearCoupons(couponData, couponModel.getQuantityPerYear());

        final BigDecimal paymentSum = oneYearCoupons
                .stream()
                .map(CouponDataModel::getPrice)
                .filter(Objects::nonNull)
                .filter(price -> StringUtils.isNotBlank(price.getCurrency()) && price.getQuantity() != null)
                .map(coupon -> convertToBondCurrency(coupon, bondPriceCurrency))
                .map(MoneyModel::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return calculateInterestPercentage(paymentSum, bondModel.getPrice());
    }

    private List<CouponDataModel> getOneYearCoupons(final List<CouponDataModel> couponData, final int paymentsPerYear) {
        if (CollectionUtils.isEmpty(couponData) || paymentsPerYear == 0) {
            return couponData;
        }

        final LocalDateTime now = dateTimeMapper.getNow();
        return couponData
                .stream()
                .filter(Objects::nonNull)
                .filter(coupon -> coupon.getPaymentDate() != null)
                .filter(coupon -> coupon.getPaymentDate().isAfter(now))
                .sorted(Comparator.comparing(CouponDataModel::getPaymentDate))
                .limit(paymentsPerYear)
                .toList();
    }

    private MoneyModel convertToBondCurrency(final MoneyModel coupon, final String targetCurrency) {
        if (targetCurrency.equalsIgnoreCase(coupon.getCurrency())) {
            return coupon;
        }

        final CurrencyModel converted = currencyUseCase.calculateAmount(
                coupon.getCurrency(), targetCurrency, coupon.getQuantity());

        return new MoneyModel()
                .setQuantity(converted.getRate())
                .setCurrency(converted.getTarget());
    }

    private BigDecimal calculateInterestPercentage(final BigDecimal paymentSum, final PriceModel currentPrice) {
        if (ObjectUtils.anyNull(paymentSum, currentPrice)
                || currentPrice.getCurrent() == null
                || currentPrice.getCurrent().getQuantity() == null
         || currentPrice.getCurrent().getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        return paymentSum
                .multiply(PERCENT)
                .divide(currentPrice.getCurrent().getQuantity(), SCALE, RoundingMode.FLOOR);
    }
}
