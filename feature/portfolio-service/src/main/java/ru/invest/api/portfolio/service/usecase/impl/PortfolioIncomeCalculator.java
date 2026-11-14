package ru.invest.api.portfolio.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.entity.PortfolioPosition;
import ru.invest.api.common.entity.PortfolioSale;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.CurrencyModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.currency.service.usecase.CurrencyPriceUseCase;
import ru.invest.api.portfolio.service.model.TradeModel;
import ru.invest.api.portfolio.service.model.TradeType;
import ru.invest.api.portfolio.service.usecase.impl.BondIncome.Flow;
import ru.invest.api.portfolio.service.usecase.impl.BondIncome.FlowKind;
import ru.invest.api.tinkoff.supplier.service.ProfitCalculationService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Денежные потоки одной облигации портфеля по покупкам, продажам и её графику выплат: покупки (цена, НКД, комиссия),
 * полученные купоны и амортизация, продажи, погашение и стоимость непроданного на сегодня. Те же правила, что у
 * калькулятора дохода: номинал на дату - без погашенных к ней частей, НКД на дату - по графику купонов, выплата в день
 * покупки - продавцу, в день продажи - нам.
 */
@Component
@RequiredArgsConstructor
public class PortfolioIncomeCalculator {
    /** Купоны и амортизация хранятся за столько лет назад - см. Constants.PAST_COUPONS_YEARS */
    static final int HISTORY_YEARS = 3;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;

    private final ProfitCalculationService profitCalculationService;
    private final CurrencyPriceUseCase currencyPriceUseCase;
    private final DateTimeMapper dateTimeMapper;

    /**
     * @param bond    облигация с ценой и графиками; null - её нет в системе
     * @param lots    покупки этой облигации
     * @param sales   продажи этой облигации
     * @param brokers имена брокеров по id
     */
    public BondIncome calculate(final String ticker, final BondModel bond, final List<PortfolioPosition> lots,
                                final List<PortfolioSale> sales, final Map<Long, String> brokers,
                                final RubRates rates) {
        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        final LotAllocation allocation = LotAllocation.of(lots, sales);
        final int sold = sales.stream().mapToInt(PortfolioSale::getQuantity).sum();
        final int bought = lots.stream().mapToInt(PortfolioPosition::getQuantity).sum();
        final LocalDate maturity = bond == null ? null : date(bond.getMaturityDate());
        final boolean redeemed = maturity != null && !maturity.isAfter(today) && bought > sold;
        final int openQuantity = redeemed ? 0 : Math.max(0, bought - sold);
        final LocalDate lastSale = sales.stream().map(PortfolioSale::getSellDate).max(Comparator.naturalOrder())
                .orElse(null);
        final LocalDate closeDate = redeemed ? maturity : lastSale;

        final String currency = currency(bond);
        final BigDecimal nominal = Optional.ofNullable(bond).map(BondModel::getPrice).map(PriceModel::getNominal)
                .map(MoneyModel::getQuantity).orElse(null);
        if (bond == null || currency == null || nominal == null) {
            return new BondIncome(ticker, false, currency, List.of(), openQuantity, false, 0, true, closeDate,
                    unknownBondTrades(ticker, bond, lots, sales, brokers));
        }

        final List<Payment> coupons = coupons(bond.getCoupon(), currency);
        final List<Payment> amortizations = amortizations(bond.getAmortizations(), currency, today);
        final Schedule schedule = new Schedule(nominal, amortizations, today);
        final List<Flow> flows = new ArrayList<>();
        final List<TradeModel> trades = new ArrayList<>();
        boolean aciUnknown = false;

        for (final PortfolioPosition lot : lots) {
            final BigDecimal amount = percentOf(schedule.nominalAt(lot.getBuyDate()), lot.getBuyPrice(),
                    lot.getQuantity());
            final BigDecimal aci = lot.getAci() != null ? lot.getAci() : aci(bond, lot.getBuyDate(), lot.getQuantity());
            aciUnknown |= aci == null;
            // рубли за саму покупку - сколько заплачено по отчёту брокера, если ввели
            flows.add(new Flow(lot.getBuyDate(), FlowKind.BUY, amount, lot.getBuyAmountRub() != null
                    ? lot.getBuyAmountRub()
                    : rates.toRubExact(amount, currency, lot.getBuyDate())));
            addFlow(flows, lot.getBuyDate(), FlowKind.BUY_ACI, aci, currency, rates);
            addFlow(flows, lot.getBuyDate(), FlowKind.BUY_COMMISSION, lot.getCommission(), currency, rates);
            trades.add(new TradeModel(TradeType.BUY, lot.getId(), ticker, bond.getName(), currency, lot.getBuyDate(),
                    lot.getQuantity(), lot.getBuyPrice(), money(amount), money(aci), lot.getAci() == null,
                    lot.getCommission(), broker(brokers, lot.getBrokerId())));
        }
        for (final PortfolioSale sale : sales) {
            final BigDecimal amount = percentOf(schedule.nominalAt(sale.getSellDate()), sale.getSellPrice(),
                    sale.getQuantity());
            final BigDecimal aci = sale.getAci() != null
                    ? sale.getAci()
                    : aci(bond, sale.getSellDate(), sale.getQuantity());
            aciUnknown |= aci == null;
            addFlow(flows, sale.getSellDate(), FlowKind.SALE, amount, currency, rates);
            addFlow(flows, sale.getSellDate(), FlowKind.SALE_ACI, aci, currency, rates);
            addFlow(flows, sale.getSellDate(), FlowKind.SALE_COMMISSION, sale.getCommission(), currency, rates);
            trades.add(new TradeModel(TradeType.SALE, sale.getId(), ticker, bond.getName(), currency,
                    sale.getSellDate(), sale.getQuantity(), sale.getSellPrice(), money(amount), money(aci),
                    sale.getAci() == null, sale.getCommission(), broker(brokers, sale.getBrokerId())));
        }

        // выплаты по графику: на дату выплаты - облигации, купленные раньше и не проданные раньше
        int unknownCoupons = 0;
        final LocalDate lastPaymentDate = maturity != null && maturity.isBefore(today) ? maturity : today;
        for (final Payment coupon : coupons) {
            if (coupon.date().isAfter(lastPaymentDate)) {
                continue;
            }
            final int held = held(lots, allocation, coupon.date());
            if (held == 0) {
                continue;
            }
            if (coupon.amount() == null) {
                unknownCoupons++;
            } else {
                addFlow(flows, coupon.date(), FlowKind.COUPON, times(coupon.amount(), held), currency, rates);
            }
        }
        for (final Payment amortization : amortizations) {
            if (amortization.date().isAfter(lastPaymentDate)) {
                continue;
            }
            final int held = held(lots, allocation, amortization.date());
            if (held > 0) {
                addFlow(flows, amortization.date(), FlowKind.AMORTIZATION, times(amortization.amount(), held),
                        currency, rates);
            }
        }

        boolean valueKnown = true;
        if (redeemed) {
            // остаток номинала в дату погашения; если последняя часть пришла амортизацией, остаток 0
            final int quantity = bought - sold;
            final BigDecimal amount = times(schedule.nominalAt(maturity), quantity);
            addFlow(flows, maturity, FlowKind.REDEMPTION, amount, currency, rates);
            trades.add(new TradeModel(TradeType.REDEMPTION, null, ticker, bond.getName(), currency, maturity,
                    quantity, HUNDRED, money(amount), BigDecimal.ZERO.setScale(MONEY_SCALE), false, null, null));
        } else if (openQuantity > 0) {
            final BigDecimal price = bond.getPrice().getPercentagePrice();
            if (price == null) {
                valueKnown = false;
            } else {
                final BigDecimal value = percentOf(nominal, price, openQuantity);
                final BigDecimal aci = aci(bond, today, openQuantity);
                aciUnknown |= aci == null;
                // стоимость сейчас - по курсу на сегодня, как текущая стоимость портфеля
                flows.add(new Flow(today, FlowKind.VALUE, value, rates.toRubExact(value, currency, null)));
                if (aci != null) {
                    flows.add(new Flow(today, FlowKind.VALUE_ACI, aci, rates.toRubExact(aci, currency, null)));
                }
            }
        }

        final LocalDate historyStart = today.minusYears(HISTORY_YEARS);
        final boolean beforeHistory = lots.stream().anyMatch(lot -> lot.getBuyDate().isBefore(historyStart));
        final boolean amortizationMissing = bond.isAmortizationFlag() && amortizations.isEmpty();
        trades.sort(Comparator.comparing(TradeModel::date).thenComparing(TradeModel::type));
        return new BondIncome(ticker, true, currency, flows, openQuantity, valueKnown, unknownCoupons,
                aciUnknown || beforeHistory || amortizationMissing, closeDate, trades);
    }

    private static int held(final List<PortfolioPosition> lots, final LotAllocation allocation, final LocalDate date) {
        return lots.stream().mapToInt(lot -> allocation.heldOn(lot, date)).sum();
    }

    /** НКД на всё количество; null - посчитать нельзя (не объявлен ближайший купон) */
    private BigDecimal aci(final BondModel bond, final LocalDate date, final int quantity) {
        return profitCalculationService.accruedInterestAt(bond, date)
                .map(perBond -> times(perBond, quantity))
                .orElse(null);
    }

    private static void addFlow(final List<Flow> flows, final LocalDate date, final FlowKind kind,
                                final BigDecimal amount, final String currency, final RubRates rates) {
        if (amount == null || amount.signum() == 0) {
            return;
        }
        flows.add(new Flow(date, kind, amount, rates.toRubExact(amount, currency, date)));
    }

    /** Облигации нет в системе: сделки - с тем, что ввёл пользователь, без сумм */
    private static List<TradeModel> unknownBondTrades(final String ticker, final BondModel bond,
                                                      final List<PortfolioPosition> lots,
                                                      final List<PortfolioSale> sales,
                                                      final Map<Long, String> brokers) {
        final String name = bond == null ? null : bond.getName();
        return Stream.concat(
                        lots.stream().map(lot -> new TradeModel(TradeType.BUY, lot.getId(), ticker, name, null,
                                lot.getBuyDate(), lot.getQuantity(), lot.getBuyPrice(), null, lot.getAci(),
                                false, lot.getCommission(), broker(brokers, lot.getBrokerId()))),
                        sales.stream().map(sale -> new TradeModel(TradeType.SALE, sale.getId(), ticker, name, null,
                                sale.getSellDate(), sale.getQuantity(), sale.getSellPrice(), null, sale.getAci(),
                                false, sale.getCommission(), broker(brokers, sale.getBrokerId()))))
                .sorted(Comparator.comparing(TradeModel::date).thenComparing(TradeModel::type))
                .toList();
    }

    private static String broker(final Map<Long, String> brokers, final Long id) {
        return id == null ? null : brokers.get(id);
    }

    /** Купоны по дате; размер не объявлен - amount null */
    private List<Payment> coupons(final CouponModel coupon, final String currency) {
        return Optional.ofNullable(coupon)
                .map(CouponModel::getCouponData)
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .filter(data -> data.getPaymentDate() != null)
                .map(data -> new Payment(data.getPaymentDate().toLocalDate(), convert(data.getPrice(), currency)))
                .sorted(Comparator.comparing(Payment::date))
                .toList();
    }

    /**
     * Номинал одной облигации на дату - без частей, погашенных к ней (как у покупок и продажи портфеля): по нему цена
     * сделки в деньгах переводится в % от номинала.
     *
     * @param bond облигация с ценой и графиком амортизации
     * @return в валюте номинала; null - номинал неизвестен
     */
    public BigDecimal nominalAt(final BondModel bond, final LocalDate date) {
        final String currency = currency(bond);
        final BigDecimal nominal = Optional.ofNullable(bond).map(BondModel::getPrice).map(PriceModel::getNominal)
                .map(MoneyModel::getQuantity).orElse(null);
        if (currency == null || nominal == null || nominal.signum() <= 0) {
            return null;
        }
        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        return new Schedule(nominal, amortizations(bond.getAmortizations(), currency, today), today).nominalAt(date);
    }

    /** Валюта номинала облигации; null - облигации нет или валюта неизвестна */
    public static String nominalCurrency(final BondModel bond) {
        return currency(bond);
    }

    /** Частичные погашения номинала с известным размером; синхронизация хранит их за 3 года назад и вперёд */
    private List<Payment> amortizations(final List<AmortizationModel> amortizations, final String currency,
                                        final LocalDate today) {
        return Optional.ofNullable(amortizations)
                .orElseGet(List::of)
                .stream()
                .filter(Objects::nonNull)
                .filter(amortization -> amortization.getPaymentDate() != null)
                .map(amortization -> new Payment(amortization.getPaymentDate().toLocalDate(),
                        convert(amortization.getPrice(), currency)))
                .filter(payment -> payment.amount() != null)
                .sorted(Comparator.comparing(Payment::date))
                .toList();
    }

    /** Сумма в валюте номинала; null - размер не объявлен или нет курса */
    private BigDecimal convert(final MoneyModel money, final String currency) {
        if (money == null || money.getQuantity() == null || money.getQuantity().signum() <= 0
                || StringUtils.isBlank(money.getCurrency())) {
            return null;
        }
        if (currency.equalsIgnoreCase(money.getCurrency())) {
            return money.getQuantity();
        }
        final CurrencyModel converted = currencyPriceUseCase.calculateAmount(money.getCurrency(), currency,
                money.getQuantity());
        return converted == null ? null : converted.getRate();
    }

    private static String currency(final BondModel bond) {
        if (bond == null) {
            return null;
        }
        return Optional.ofNullable(bond.getPrice())
                .map(PriceModel::getNominal)
                .map(MoneyModel::getCurrency)
                .filter(StringUtils::isNotBlank)
                .orElse(bond.getCurrency());
    }

    private static LocalDate date(final LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    private static BigDecimal percentOf(final BigDecimal nominal, final BigDecimal percent, final int quantity) {
        return nominal.multiply(percent).multiply(BigDecimal.valueOf(quantity)).divide(HUNDRED);
    }

    private static BigDecimal times(final BigDecimal amount, final int quantity) {
        return amount.multiply(BigDecimal.valueOf(quantity));
    }

    private static BigDecimal money(final BigDecimal amount) {
        return amount == null ? null : amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private record Payment(LocalDate date, BigDecimal amount) {
    }

    /**
     * Номинал одной облигации на конец дня: текущий номинал плюс части, погашенные после этого дня и до сегодня (их
     * текущий номинал уже не содержит), минус будущие - не позже этого дня.
     */
    private record Schedule(BigDecimal nominal, List<Payment> amortizations, LocalDate today) {
        BigDecimal nominalAt(final LocalDate date) {
            BigDecimal result = nominal;
            for (final Payment amortization : amortizations) {
                final boolean past = !amortization.date().isAfter(today);
                if (past && amortization.date().isAfter(date)) {
                    result = result.add(amortization.amount());
                } else if (!past && !amortization.date().isAfter(date)) {
                    result = result.subtract(amortization.amount());
                }
            }
            return result.max(BigDecimal.ZERO);
        }
    }
}
