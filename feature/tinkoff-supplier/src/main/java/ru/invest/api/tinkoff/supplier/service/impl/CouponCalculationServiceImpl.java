package ru.invest.api.tinkoff.supplier.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.currency.service.usecase.CurrencyPriceUseCase;
import ru.invest.api.tinkoff.supplier.service.CouponCalculationService;
import ru.invest.api.tinkoff.supplier.service.YieldMetrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class CouponCalculationServiceImpl implements CouponCalculationService {
    private static final int SCALE = 10;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100.0);
    private static final int YIELD_SCALE = 4;
    private static final int DURATION_SCALE = 4;
    private static final double DAYS_IN_YEAR = 365.0;
    // границы поиска ставки доходности: от -99% до 1000% годовых
    private static final double MIN_RATE = -0.99;
    private static final double MAX_RATE = 10.0;
    private static final double RATE_PRECISION = 1e-10;
    // расхождение суммы погашений с номиналом в пределах копейки - округление, а не ошибка данных
    private static final BigDecimal NOMINAL_TOLERANCE = new BigDecimal("0.01");

    private final CurrencyPriceUseCase currencyPriceUseCase;
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

        final List<MoneyModel> payments = oneYearCoupons
                .stream()
                .map(CouponDataModel::getPrice)
                .filter(Objects::nonNull)
                .filter(price -> StringUtils.isNotBlank(price.getCurrency()) && price.getQuantity() != null)
                .map(coupon -> convertToBondCurrency(coupon, bondPriceCurrency))
                .toList();

        // нет курса хотя бы для одной выплаты - доходность посчитать нельзя, без неё сумма была бы занижена
        if (payments.contains(null)) {
            return null;
        }

        final BigDecimal paymentSum = payments
                .stream()
                .map(MoneyModel::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return calculateInterestPercentage(toYearPaymentSum(paymentSum, payments.size(), couponModel.getQuantityPerYear()),
                bondModel.getPrice());
    }

    /**
     * До погашения осталось меньше купонов, чем выплат в год - приводим их сумму к году,
     * иначе облигация за пару месяцев до погашения показывала бы долю годового купона.
     */
    private BigDecimal toYearPaymentSum(final BigDecimal paymentSum, final int paymentCount, final int paymentsPerYear) {
        if (paymentsPerYear <= 0 || paymentCount == 0 || paymentCount >= paymentsPerYear) {
            return paymentSum;
        }

        return paymentSum
                .multiply(BigDecimal.valueOf(paymentsPerYear))
                .divide(BigDecimal.valueOf(paymentCount), SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateYieldToMaturity(final CouponModel couponModel, final BondModel bondModel) {
        return calculateYield(couponModel, bondModel, bondModel.getMaturityDate()).yield();
    }

    @Override
    public YieldMetrics calculateMaturityMetrics(final CouponModel couponModel, final BondModel bondModel) {
        return calculateYield(couponModel, bondModel, bondModel.getMaturityDate());
    }

    @Override
    public BigDecimal calculateYieldToOffer(final CouponModel couponModel, final BondModel bondModel) {
        final LocalDateTime offerDate = bondModel.getOfferDate();
        // оферта в дату погашения или позже ничем не отличается от погашения
        if (offerDate == null || bondModel.getMaturityDate() != null && !offerDate.isBefore(bondModel.getMaturityDate())) {
            return null;
        }

        return calculateYield(couponModel, bondModel, offerDate).yield();
    }

    /**
     * Доходность, если держать облигацию до даты horizon и получить в эту дату остаток номинала.
     * Цена покупки - текущая цена плюс НКД; выплаты - купоны и частичные погашения номинала до horizon включительно.
     */
    private YieldMetrics calculateYield(final CouponModel couponModel, final BondModel bondModel, final LocalDateTime horizon) {
        final PriceModel price = bondModel.getPrice();
        // НКД null - облигации ещё не синхронизированы, без него доходность была бы завышена
        if (ObjectUtils.anyNull(couponModel, price, horizon)
                || ObjectUtils.anyNull(price.getAci(), price.getNominal())
                || !isPositive(price.getCurrent())
                || !isPositive(price.getNominal())) {
            return YieldMetrics.EMPTY;
        }

        final LocalDateTime now = dateTimeMapper.getNow();
        if (!horizon.isAfter(now)) {
            return YieldMetrics.EMPTY;
        }

        final List<AmortizationModel> futureAmortizations = getFuturePayments(bondModel.getAmortizations(),
                AmortizationModel::getPaymentDate, now);
        // график амортизации ещё не загружен - весь номинал ушёл бы на дату погашения и исказил доходность
        if (bondModel.isAmortizationFlag() && futureAmortizations.isEmpty()) {
            return YieldMetrics.EMPTY;
        }

        final String bondPriceCurrency = price.getCurrent().getCurrency();
        final BigDecimal dirtyPrice = getDirtyPrice(price, bondPriceCurrency);
        final MoneyModel nominal = convertToBondCurrency(price.getNominal(), bondPriceCurrency);
        if (ObjectUtils.anyNull(dirtyPrice, nominal)) {
            return YieldMetrics.EMPTY;
        }

        final List<CashFlow> cashFlows = new ArrayList<>();

        final List<CouponDataModel> coupons = getFuturePayments(couponModel.getCouponData(), CouponDataModel::getPaymentDate, now);
        for (final CouponDataModel coupon : coupons) {
            if (isAfter(coupon.getPaymentDate(), horizon)) {
                continue;
            }
            // размер купона ещё не объявлен (флоатер) - без него доходность была бы занижена
            final MoneyModel payment = isPositive(coupon.getPrice())
                    ? convertToBondCurrency(coupon.getPrice(), bondPriceCurrency)
                    : null;
            if (payment == null) {
                return YieldMetrics.EMPTY;
            }
            cashFlows.add(toCashFlow(now, coupon.getPaymentDate(), payment.getQuantity()));
        }

        BigDecimal repaid = BigDecimal.ZERO;
        for (final AmortizationModel amortization : futureAmortizations) {
            if (isAfter(amortization.getPaymentDate(), horizon)) {
                continue;
            }
            final MoneyModel payment = isPositive(amortization.getPrice())
                    ? convertToBondCurrency(amortization.getPrice(), bondPriceCurrency)
                    : null;
            if (payment == null) {
                return YieldMetrics.EMPTY;
            }
            cashFlows.add(toCashFlow(now, amortization.getPaymentDate(), payment.getQuantity()));
            repaid = repaid.add(payment.getQuantity());
        }

        // остаток номинала гасится в дату horizon: при погашении - всё, что не покрыл график, при оферте - выкуп по номиналу
        final BigDecimal remainder = nominal.getQuantity().subtract(repaid);
        if (remainder.compareTo(NOMINAL_TOLERANCE.negate()) < 0) {
            // погашений по графику больше текущего номинала - данные противоречивы
            return YieldMetrics.EMPTY;
        }
        if (remainder.signum() > 0) {
            cashFlows.add(toCashFlow(now, horizon, remainder));
        }

        return solveYield(cashFlows, dirtyPrice.doubleValue());
    }

    private <T> List<T> getFuturePayments(final List<T> payments, final Function<T, LocalDateTime> paymentDate,
                                          final LocalDateTime now) {
        return Optional.ofNullable(payments)
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .filter(payment -> paymentDate.apply(payment) != null)
                .filter(payment -> paymentDate.apply(payment).isAfter(now))
                .toList();
    }

    /**
     * Цена покупки с НКД в валюте цены облигации; null - НКД не перевести в валюту цены.
     */
    private BigDecimal getDirtyPrice(final PriceModel price, final String bondPriceCurrency) {
        final MoneyModel aci = price.getAci();
        if (aci.getQuantity() == null || aci.getQuantity().signum() == 0) {
            return price.getCurrent().getQuantity();
        }
        if (StringUtils.isBlank(aci.getCurrency())) {
            return null;
        }

        final MoneyModel convertedAci = convertToBondCurrency(aci, bondPriceCurrency);
        return convertedAci == null ? null : price.getCurrent().getQuantity().add(convertedAci.getQuantity());
    }

    // выплаты сравниваются по дням: купон в день погашения или оферты ещё входит в расчёт
    private boolean isAfter(final LocalDateTime paymentDate, final LocalDateTime horizon) {
        return paymentDate.toLocalDate().isAfter(horizon.toLocalDate());
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

        final CurrencyModel converted = currencyPriceUseCase.calculateAmount(
                coupon.getCurrency(), targetCurrency, coupon.getQuantity());
        if (converted == null) {
            return null;
        }

        return new MoneyModel()
                .setQuantity(converted.getRate())
                .setCurrency(converted.getTarget());
    }

    private boolean isPositive(final MoneyModel money) {
        return money != null
                && StringUtils.isNotBlank(money.getCurrency())
                && money.getQuantity() != null
                && money.getQuantity().signum() > 0;
    }

    private CashFlow toCashFlow(final LocalDateTime now, final LocalDateTime paymentDate, final BigDecimal amount) {
        final long days = ChronoUnit.DAYS.between(now.toLocalDate(), paymentDate.toLocalDate());
        return new CashFlow(days / DAYS_IN_YEAR, amount.doubleValue());
    }

    /**
     * Ставка ищется делением отрезка пополам: приведённая стоимость выплат монотонно убывает с ростом ставки.
     */
    private YieldMetrics solveYield(final List<CashFlow> cashFlows, final double price) {
        // доходность вне границ поиска - такую облигацию честно оценить нельзя
        if (presentValue(cashFlows, MAX_RATE) > price || presentValue(cashFlows, MIN_RATE) < price) {
            return YieldMetrics.EMPTY;
        }

        double low = MIN_RATE;
        double high = MAX_RATE;
        while (high - low > RATE_PRECISION) {
            final double middle = (low + high) / 2;
            if (presentValue(cashFlows, middle) > price) {
                low = middle;
            } else {
                high = middle;
            }
        }

        final double rate = (low + high) / 2;
        final double macaulay = macaulayDuration(cashFlows, rate);
        return new YieldMetrics(
                BigDecimal.valueOf(rate).multiply(PERCENT).setScale(YIELD_SCALE, RoundingMode.HALF_UP),
                BigDecimal.valueOf(macaulay).setScale(DURATION_SCALE, RoundingMode.HALF_UP),
                BigDecimal.valueOf(macaulay / (1 + rate)).setScale(DURATION_SCALE, RoundingMode.HALF_UP));
    }

    /**
     * Дюрация Маколея: срок каждой выплаты в годах, взвешенный по её приведённой стоимости. Сумма приведённых
     * стоимостей по найденной ставке - это и есть цена с НКД, поэтому веса в сумме дают 1.
     */
    private double macaulayDuration(final List<CashFlow> cashFlows, final double rate) {
        double weighted = 0;
        double total = 0;
        for (final CashFlow cashFlow : cashFlows) {
            final double value = cashFlow.amount() / Math.pow(1 + rate, cashFlow.years());
            weighted += cashFlow.years() * value;
            total += value;
        }
        return weighted / total;
    }

    private double presentValue(final List<CashFlow> cashFlows, final double rate) {
        return cashFlows
                .stream()
                .mapToDouble(cashFlow -> cashFlow.amount() / Math.pow(1 + rate, cashFlow.years()))
                .sum();
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

    private record CashFlow(double years, double amount) {
    }
}
