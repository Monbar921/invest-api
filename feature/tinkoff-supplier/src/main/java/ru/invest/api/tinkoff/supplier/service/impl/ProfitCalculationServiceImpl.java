package ru.invest.api.tinkoff.supplier.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.calculation.ExitType;
import ru.invest.api.common.model.calculation.ProfitCalculationModel;
import ru.invest.api.common.model.calculation.ProfitCalculationRequestModel;
import ru.invest.api.currency.service.usecase.CurrencyPriceUseCase;
import ru.invest.api.tinkoff.supplier.service.ProfitCalculationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static ru.invest.api.common.exception.enums.ExceptionErrorCode.INVALID_CALCULATION_PARAMETERS;

@Service
@RequiredArgsConstructor
public class ProfitCalculationServiceImpl implements ProfitCalculationService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    private static final int SCALE = 10;
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;

    private final CurrencyPriceUseCase currencyPriceUseCase;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public ProfitCalculationModel calculate(final BondModel bond, final ProfitCalculationRequestModel request) {
        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        final PriceModel price = bond.getPrice();
        if (price == null || price.getNominal() == null || price.getNominal().getQuantity() == null
                || StringUtils.isBlank(price.getNominal().getCurrency())) {
            throw invalid("Bond %s has no nominal".formatted(bond.getTicker()));
        }
        final String currency = price.getNominal().getCurrency();

        final int quantity = Optional.ofNullable(request.getQuantity()).orElse(1);
        final LocalDate buyDate = Optional.ofNullable(request.getBuyDate()).orElse(today);
        final LocalDate maturityDate = Optional.ofNullable(bond.getMaturityDate()).map(LocalDateTime::toLocalDate).orElse(null);
        final LocalDate offerDate = Optional.ofNullable(bond.getOfferDate()).map(LocalDateTime::toLocalDate).orElse(null);
        final LocalDate requestedSellDate = Optional.ofNullable(request.getSellDate()).orElse(maturityDate);

        validate(bond, quantity, today, buyDate, requestedSellDate, maturityDate);
        validateRate(request.getTaxRatePercent(), "taxRatePercent");
        validateRate(request.getDepositRatePercent(), "depositRatePercent");

        final ExitType exitType = getExitType(requestedSellDate, maturityDate, offerDate);
        final LocalDate sellDate = exitType == ExitType.MATURITY ? maturityDate : requestedSellDate;

        final List<Payment> coupons = getCoupons(bond.getCoupon(), currency);
        final List<Payment> amortizations = getAmortizations(bond.getAmortizations(), today, currency);
        final BigDecimal nominal = price.getNominal().getQuantity();

        // покупка: номинал на дату покупки уже без погашенных к ней частей
        final BigDecimal buyNominal = nominalAt(nominal, amortizations, buyDate);
        final BigDecimal buyPricePercent = getPricePercent(request.getBuyPricePercent(), price, "buy");
        final BigDecimal purchaseAmount = percentOf(buyNominal, buyPricePercent);
        final AccruedInterest purchaseAci = aciAt(buyDate, today, price, coupons, bond.getCoupon(), currency);

        // за период: выплаты после даты покупки и до даты выхода включительно
        final List<Payment> periodCoupons = between(coupons, buyDate, sellDate);
        final BigDecimal couponIncome = sum(periodCoupons.stream().filter(Payment::isKnown).toList());
        final int unknownCouponCount = (int) periodCoupons.stream().filter(coupon -> !coupon.isKnown()).count();
        final BigDecimal amortizationIncome = sum(between(amortizations, buyDate, sellDate));

        // выход: при погашении возвращается весь непогашенный остаток номинала, иначе продаём остаток на дату выхода
        final BigDecimal sellPricePercent;
        final BigDecimal exitAmount;
        final AccruedInterest exitAci;
        if (exitType == ExitType.MATURITY) {
            sellPricePercent = HUNDRED;
            exitAmount = buyNominal.subtract(amortizationIncome).max(BigDecimal.ZERO);
            exitAci = AccruedInterest.ZERO;
        } else {
            sellPricePercent = exitType == ExitType.OFFER ? HUNDRED : getPricePercent(request.getSellPricePercent(), price, "sell");
            exitAmount = percentOf(nominalAt(nominal, amortizations, sellDate), sellPricePercent);
            exitAci = aciAt(sellDate, today, price, coupons, bond.getCoupon(), currency);
        }

        final BigDecimal purchase = purchaseAmount.add(purchaseAci.amount());
        if (purchase.signum() <= 0) {
            throw invalid("Purchase amount must be positive");
        }
        final BigDecimal profit = couponIncome.add(amortizationIncome).add(exitAmount).add(exitAci.amount()).subtract(purchase);
        // упрощение: налог с итогового положительного дохода; вычет НКД при покупке так и учитывается
        final BigDecimal taxRatePercent = request.getTaxRatePercent();
        final BigDecimal tax = taxRatePercent == null ? BigDecimal.ZERO : percentOf(profit.max(BigDecimal.ZERO), taxRatePercent);
        final BigDecimal netProfit = profit.subtract(tax);
        final long days = ChronoUnit.DAYS.between(buyDate, sellDate);
        final BigDecimal profitPercent = toPercent(profit, purchase);

        final ProfitCalculationModel result = new ProfitCalculationModel()
                .setTicker(bond.getTicker())
                .setCurrency(currency)
                .setQuantity(quantity)
                .setBuyDate(buyDate)
                .setSellDate(sellDate)
                .setDays(days)
                .setExitType(exitType)
                .setBuyPricePercent(buyPricePercent)
                .setSellPricePercent(sellPricePercent)
                .setPurchaseAmount(money(purchaseAmount, quantity))
                .setPurchaseAci(money(purchaseAci.amount(), quantity))
                .setCouponIncome(money(couponIncome, quantity))
                .setCouponCount(periodCoupons.size())
                .setAmortizationIncome(money(amortizationIncome, quantity))
                .setExitAmount(money(exitAmount, quantity))
                .setExitAci(money(exitAci.amount(), quantity))
                .setProfit(money(profit, quantity))
                .setProfitPercent(profitPercent.setScale(PERCENT_SCALE, RoundingMode.HALF_UP))
                .setAnnualProfitPercent(profitPercent.multiply(DAYS_IN_YEAR)
                        .divide(BigDecimal.valueOf(days), PERCENT_SCALE, RoundingMode.HALF_UP))
                .setTaxRatePercent(taxRatePercent)
                .setTax(money(tax, quantity))
                .setNetProfit(money(netProfit, quantity))
                .setNetProfitPercent(toPercent(netProfit, purchase).setScale(PERCENT_SCALE, RoundingMode.HALF_UP))
                .setUnknownCouponCount(unknownCouponCount)
                .setAciUnknown(!purchaseAci.known() || !exitAci.known())
                .setAmortizationScheduleMissing(bond.isAmortizationFlag() && amortizations.isEmpty());
        fillDeposit(result, request.getDepositRatePercent(), purchase, days, taxRatePercent, quantity);
        return result;
    }

    @Override
    public Optional<BigDecimal> accruedInterestAt(final BondModel bond, final LocalDate date) {
        final PriceModel price = bond.getPrice();
        if (price == null || price.getNominal() == null || StringUtils.isBlank(price.getNominal().getCurrency())) {
            return Optional.empty();
        }
        final String currency = price.getNominal().getCurrency();
        final AccruedInterest aci = aciAt(date, dateTimeMapper.getNow().toLocalDate(), price,
                getCoupons(bond.getCoupon(), currency), bond.getCoupon(), currency);
        return aci.known() ? Optional.of(aci.amount()) : Optional.empty();
    }

    /**
     * Вклад на ту же сумму, что ушла на покупку (с НКД), на те же дни: простые проценты, без капитализации - как и доход
     * облигации считается без реинвестирования купонов. НДФЛ - по той же ставке, без необлагаемого лимита по вкладам.
     */
    private void fillDeposit(final ProfitCalculationModel result, final BigDecimal depositRatePercent, final BigDecimal purchase,
                             final long days, final BigDecimal taxRatePercent, final int quantity) {
        if (depositRatePercent == null) {
            return;
        }
        final BigDecimal profit = percentOf(purchase, depositRatePercent)
                .multiply(BigDecimal.valueOf(days))
                .divide(DAYS_IN_YEAR, SCALE, RoundingMode.HALF_UP);
        final BigDecimal tax = taxRatePercent == null ? BigDecimal.ZERO : percentOf(profit, taxRatePercent);
        result.setDepositRatePercent(depositRatePercent)
                .setDepositProfit(money(profit, quantity))
                .setDepositTax(money(tax, quantity))
                .setDepositNetProfit(money(profit.subtract(tax), quantity));
    }

    private void validate(final BondModel bond, final int quantity, final LocalDate today, final LocalDate buyDate,
                          final LocalDate sellDate, final LocalDate maturityDate) {
        if (quantity <= 0) {
            throw invalid("quantity must be a positive number");
        }
        // цены и номинала на прошлые даты у нас нет
        if (buyDate.isBefore(today)) {
            throw invalid("buyDate must not be in the past");
        }
        if (maturityDate != null && !buyDate.isBefore(maturityDate)) {
            throw invalid("Bond %s matures on %s, buyDate must be earlier".formatted(bond.getTicker(), maturityDate));
        }
        if (sellDate == null) {
            throw invalid("Bond %s has no maturity date, provide sellDate".formatted(bond.getTicker()));
        }
        if (!sellDate.isAfter(buyDate)) {
            throw invalid("sellDate must be after buyDate");
        }
    }

    private void validateRate(final BigDecimal ratePercent, final String name) {
        if (ratePercent != null && (ratePercent.signum() < 0 || ratePercent.compareTo(HUNDRED) >= 0)) {
            throw invalid("%s must be from 0 to 100".formatted(name));
        }
    }

    private ExitType getExitType(final LocalDate sellDate, final LocalDate maturityDate, final LocalDate offerDate) {
        if (maturityDate != null && !sellDate.isBefore(maturityDate)) {
            return ExitType.MATURITY;
        }
        return sellDate.equals(offerDate) ? ExitType.OFFER : ExitType.SALE;
    }

    private BigDecimal getPricePercent(final BigDecimal requested, final PriceModel price, final String side) {
        final BigDecimal percent = Optional.ofNullable(requested).orElse(price.getPercentagePrice());
        if (percent == null) {
            throw invalid("Bond has no current price, provide %sPricePercent".formatted(side));
        }
        if (percent.signum() <= 0) {
            throw invalid("%sPricePercent must be positive".formatted(side));
        }
        return percent;
    }

    private List<Payment> getCoupons(final CouponModel coupon, final String currency) {
        return Optional.ofNullable(coupon)
                .map(CouponModel::getCouponData)
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .filter(couponData -> couponData.getPaymentDate() != null)
                .map(couponData -> new Payment(couponData.getPaymentDate().toLocalDate(), convert(couponData.getPrice(), currency)))
                .sorted(Comparator.comparing(Payment::date))
                .toList();
    }

    private List<Payment> getAmortizations(final List<AmortizationModel> amortizations, final LocalDate today,
                                           final String currency) {
        return Optional.ofNullable(amortizations)
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .filter(amortization -> amortization.getPaymentDate() != null)
                .filter(amortization -> amortization.getPaymentDate().toLocalDate().isAfter(today))
                .map(amortization -> new Payment(amortization.getPaymentDate().toLocalDate(),
                        convert(amortization.getPrice(), currency)))
                .filter(Payment::isKnown)
                .sorted(Comparator.comparing(Payment::date))
                .toList();
    }

    /**
     * Номинал на конец дня date: текущий номинал без частей, погашенных к этой дате.
     */
    private BigDecimal nominalAt(final BigDecimal nominal, final List<Payment> amortizations, final LocalDate date) {
        final BigDecimal repaid = sum(amortizations.stream().filter(amortization -> !amortization.date().isAfter(date)).toList());
        return nominal.subtract(repaid).max(BigDecimal.ZERO);
    }

    /**
     * НКД на дату: на сегодня - значение из T-Invest API, на другие даты - доля ближайшего купона пропорционально дням
     * с начала купонного периода. Начало периода - дата прошлого купона, для первого купона - дата купона минус период.
     */
    private AccruedInterest aciAt(final LocalDate date, final LocalDate today, final PriceModel price, final List<Payment> coupons,
                                  final CouponModel coupon, final String currency) {
        if (date.equals(today) && price.getAci() != null && price.getAci().getQuantity() != null) {
            final BigDecimal aci = price.getAci().getQuantity().signum() == 0
                    ? BigDecimal.ZERO
                    : Optional.ofNullable(convert(price.getAci(), currency)).map(MoneyModel::getQuantity).orElse(null);
            if (aci != null) {
                return new AccruedInterest(aci, true);
            }
        }

        final Payment next = coupons.stream().filter(payment -> payment.date().isAfter(date)).findFirst().orElse(null);
        if (next == null) {
            return AccruedInterest.ZERO;
        }
        if (!next.isKnown()) {
            return AccruedInterest.UNKNOWN;
        }

        final LocalDate periodStart = coupons.stream()
                .filter(payment -> !payment.date().isAfter(date))
                .reduce((first, second) -> second)
                .map(Payment::date)
                .orElseGet(() -> firstPeriodStart(next.date(), coupon));
        if (periodStart == null) {
            return AccruedInterest.UNKNOWN;
        }

        final long periodDays = ChronoUnit.DAYS.between(periodStart, next.date());
        final long accruedDays = Math.max(0, ChronoUnit.DAYS.between(periodStart, date));
        if (periodDays <= 0) {
            return AccruedInterest.ZERO;
        }
        return new AccruedInterest(next.amount()
                .multiply(BigDecimal.valueOf(Math.min(accruedDays, periodDays)))
                .divide(BigDecimal.valueOf(periodDays), SCALE, RoundingMode.HALF_UP), true);
    }

    private LocalDate firstPeriodStart(final LocalDate firstCouponDate, final CouponModel coupon) {
        if (coupon == null || coupon.getQuantityPerYear() <= 0) {
            return null;
        }
        return firstCouponDate.minusDays(Math.round(DAYS_IN_YEAR.doubleValue() / coupon.getQuantityPerYear()));
    }

    // выплата после даты покупки и не позже даты выхода
    private List<Payment> between(final List<Payment> payments, final LocalDate from, final LocalDate to) {
        return payments.stream()
                .filter(payment -> payment.date().isAfter(from) && !payment.date().isAfter(to))
                .toList();
    }

    private BigDecimal sum(final List<Payment> payments) {
        return payments.stream()
                .map(Payment::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Сумма в валюте номинала; null - размер выплаты не объявлен или нет курса.
     */
    private MoneyModel convert(final MoneyModel money, final String currency) {
        if (money == null || money.getQuantity() == null || money.getQuantity().signum() <= 0
                || StringUtils.isBlank(money.getCurrency())) {
            return null;
        }
        if (currency.equalsIgnoreCase(money.getCurrency())) {
            return money;
        }

        final CurrencyModel converted = currencyPriceUseCase.calculateAmount(money.getCurrency(), currency, money.getQuantity());
        return converted == null ? null : new MoneyModel().setQuantity(converted.getRate()).setCurrency(converted.getTarget());
    }

    private BigDecimal percentOf(final BigDecimal amount, final BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal toPercent(final BigDecimal amount, final BigDecimal base) {
        return amount.multiply(HUNDRED).divide(base, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(final BigDecimal amount, final int quantity) {
        return amount.multiply(BigDecimal.valueOf(quantity)).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private GeneralUnprocessableEntityException invalid(final String message) {
        return new GeneralUnprocessableEntityException(INVALID_CALCULATION_PARAMETERS, message);
    }

    private record Payment(LocalDate date, BigDecimal amount) {
        Payment(final LocalDate date, final MoneyModel money) {
            this(date, money == null ? null : money.getQuantity());
        }

        boolean isKnown() {
            return amount != null && amount.signum() > 0;
        }
    }

    /**
     * НКД; known = false - размер ближайшего купона не объявлен, НКД принят нулевым.
     */
    private record AccruedInterest(BigDecimal amount, boolean known) {
        static final AccruedInterest ZERO = new AccruedInterest(BigDecimal.ZERO, true);
        static final AccruedInterest UNKNOWN = new AccruedInterest(BigDecimal.ZERO, false);
    }
}
