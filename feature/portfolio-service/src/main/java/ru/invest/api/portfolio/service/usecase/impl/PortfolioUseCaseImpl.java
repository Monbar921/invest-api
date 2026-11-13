package ru.invest.api.portfolio.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.PortfolioBroker;
import ru.invest.api.common.entity.PortfolioPosition;
import ru.invest.api.common.entity.PortfolioSale;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.account.PlanLimit;
import ru.invest.api.common.repository.PortfolioBrokerRepository;
import ru.invest.api.common.repository.PortfolioPositionRepository;
import ru.invest.api.common.repository.PortfolioSaleRepository;
import ru.invest.api.common.usecase.EntitlementUseCase;
import ru.invest.api.portfolio.service.model.BrokerModel;
import ru.invest.api.portfolio.service.model.ClosedPositionModel;
import ru.invest.api.portfolio.service.model.IncomeModel;
import ru.invest.api.portfolio.service.model.PortfolioModel;
import ru.invest.api.portfolio.service.model.PortfolioTotalModel;
import ru.invest.api.portfolio.service.model.PositionModel;
import ru.invest.api.portfolio.service.model.PositionRequestModel;
import ru.invest.api.portfolio.service.usecase.PortfolioUseCase;
import ru.invest.api.tinkoff.supplier.service.PriceConversionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PortfolioUseCaseImpl implements PortfolioUseCase {
    private static final String NOT_FOUND = "Portfolio position %d not found";
    private static final int MAX_QUANTITY = 1_000_000;
    // цена покупки в % от номинала; верхний предел - как у порога цены в уведомлениях
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(300);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final Set<String> RUB_CODES = Set.of("RUB", "RUR");

    private final PortfolioPositionRepository repository;
    private final PortfolioSaleRepository saleRepository;
    private final PortfolioBrokerRepository brokerRepository;
    private final BrokerResolver brokerResolver;
    private final BondUseCase bondUseCase;
    private final DateTimeMapper dateTimeMapper;
    private final PriceConversionService priceConversionService;
    private final EntitlementUseCase entitlementUseCase;
    private final PortfolioIncomeCalculator incomeCalculator;

    @Override
    @Transactional(readOnly = true)
    public PortfolioModel getPortfolio(final String userId, final BigDecimal taxRatePercent) {
        validateTaxRate(taxRatePercent);
        final List<PortfolioPosition> positions = repository.findByUserIdOrderByTickerAscBuyDateAscIdAsc(userId);
        final List<PortfolioSale> sales = saleRepository.findByUserIdOrderByTickerAscSellDateAscIdAsc(userId);
        final Map<String, List<PortfolioPosition>> lotsByTicker = positions.stream()
                .collect(Collectors.groupingBy(PortfolioPosition::getTicker, LinkedHashMap::new, Collectors.toList()));
        final Map<String, List<PortfolioSale>> salesByTicker = sales.stream()
                .collect(Collectors.groupingBy(PortfolioSale::getTicker));
        // с графиком купонов и амортизации - для купона, доходности к погашению и полного дохода
        final Map<String, BondModel> bonds = new HashMap<>();
        final Set<String> known = lotsByTicker.isEmpty()
                ? Set.of()
                : bondUseCase.getByTickers(lotsByTicker.keySet()).keySet();
        lotsByTicker.keySet().stream().filter(known::contains)
                .forEach(ticker -> bonds.put(ticker, bondUseCase.getWithSchedule(ticker)));
        final Map<Long, String> brokers = brokerResolver.names(userId);
        final RubRates rates = new RubRates(priceConversionService);

        final Map<String, BondIncome> incomes = new LinkedHashMap<>();
        lotsByTicker.forEach((ticker, lots) -> incomes.put(ticker, incomeCalculator.calculate(ticker,
                bonds.get(ticker), lots, salesByTicker.getOrDefault(ticker, List.of()), brokers, rates)));

        final List<PositionModel> open = new ArrayList<>();
        final List<ClosedPositionModel> closed = new ArrayList<>();
        final Map<String, IncomeModel> groupIncomes = new HashMap<>();
        lotsByTicker.forEach((ticker, lots) -> {
            final BondIncome bondIncome = incomes.get(ticker);
            final BondModel bond = bonds.get(ticker);
            final IncomeModel income = IncomeAggregator.of(bondIncome.currency(), List.of(bondIncome), false,
                    taxRatePercent);
            if (bondIncome.openQuantity() > 0) {
                final LotAllocation allocation = LotAllocation.of(lots, salesByTicker.getOrDefault(ticker, List.of()));
                lots.forEach(lot -> open.add(toModel(lot, bond, broker(brokers, lot), allocation.remaining(lot),
                        rates)));
                groupIncomes.put(ticker, income);
            } else {
                closed.add(new ClosedPositionModel(ticker, bond == null ? null : bond.getName(),
                        bond == null ? null : bond.getIsin(), bondIncome.currency(),
                        lots.stream().mapToInt(PortfolioPosition::getQuantity).sum(),
                        lots.stream().map(PortfolioPosition::getBuyDate).min(Comparator.naturalOrder()).orElse(null),
                        bondIncome.closeDate(), income));
            }
        });
        // последние закрытые - первыми
        closed.sort(Comparator.comparing(ClosedPositionModel::closeDate,
                Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(ClosedPositionModel::ticker));

        final List<PortfolioTotalModel> totals = PortfolioAggregator.totals(open);
        return new PortfolioModel(PortfolioAggregator.groups(open, metrics(bonds), groupIncomes), totals,
                PortfolioAggregator.summary(totals), closed, incomeTotals(incomes.values(), taxRatePercent),
                IncomeAggregator.of(null, List.copyOf(incomes.values()), true, taxRatePercent));
    }

    @Override
    @Transactional
    public PositionModel create(final String userId, final PositionRequestModel request) {
        if (request.ticker() == null || request.ticker().isBlank()) {
            throw unprocessable("ticker is required");
        }
        final LocalDate buyDate = validate(request);
        // 404, если такой облигации нет
        final BondModel bond = bondUseCase.getWithSchedule(request.ticker());
        // 402, если покупок уже столько, сколько разрешает тариф
        entitlementUseCase.requireBelowLimit(userId, PlanLimit.PORTFOLIO_POSITIONS, repository.countByUserId(userId));
        validateBuyAmountRub(request.buyAmountRub(), currency(bond));
        validateNotAfterMaturity(buyDate, bond);
        final PortfolioBroker broker = brokerResolver.resolve(userId, request.broker());
        final PortfolioPosition position = repository.save(new PortfolioPosition()
                .setUserId(userId)
                .setTicker(bond.getTicker())
                .setQuantity(request.quantity())
                .setBuyPrice(request.buyPrice())
                .setBuyDate(buyDate)
                .setBuyAmountRub(request.buyAmountRub())
                .setAci(request.aci())
                .setCommission(request.commission())
                .setBrokerId(broker == null ? null : broker.getId())
                .setCreatedAt(LocalDateTime.now(ZoneOffset.UTC)));
        return toModel(position, bond, broker == null ? null : broker.getName(),
                remaining(userId, position, null), new RubRates(priceConversionService));
    }

    @Override
    @Transactional
    public PositionModel update(final String userId, final Long id, final PositionRequestModel request) {
        final PortfolioPosition position = find(userId, id);
        final LocalDate buyDate = validate(request);
        final BondModel bond = findWithSchedule(position.getTicker()).orElse(null);
        validateBuyAmountRub(request.buyAmountRub(), currency(bond));
        validateNotAfterMaturity(buyDate, bond);
        final PortfolioBroker broker = brokerResolver.resolve(userId, request.broker());
        position.setQuantity(request.quantity())
                .setBuyPrice(request.buyPrice())
                .setBuyDate(buyDate)
                .setBuyAmountRub(request.buyAmountRub())
                .setAci(request.aci())
                .setCommission(request.commission())
                .setBrokerId(broker == null ? null : broker.getId())
                .setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        // меньше облигаций или дата позже - продажам может не хватить купленного
        final int remaining = remaining(userId, position, null);
        repository.save(position);
        return toModel(position, bond, broker == null ? null : broker.getName(), remaining,
                new RubRates(priceConversionService));
    }

    @Override
    @Transactional
    public void delete(final String userId, final Long id) {
        final PortfolioPosition position = find(userId, id);
        remaining(userId, position, position.getId());
        repository.delete(position);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrokerModel> getBrokers(final String userId) {
        return brokerRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(broker -> new BrokerModel(broker.getId(), broker.getName()))
                .toList();
    }

    /**
     * Сколько из покупки не продано - и проверка, что всем продажам облигации хватает покупок на их даты.
     *
     * @param position  покупка, уже с изменениями
     * @param deletedId покупка, которую удаляют: её облигаций у продаж больше нет; null - ничего не удаляют
     * @throws GeneralUnprocessableEntityException продаже не хватает облигаций
     */
    private int remaining(final String userId, final PortfolioPosition position, final Long deletedId) {
        final List<PortfolioSale> sales = saleRepository.findByUserIdAndTicker(userId, position.getTicker());
        if (sales.isEmpty()) {
            return deletedId == null ? position.getQuantity() : 0;
        }
        final List<PortfolioPosition> lots = Stream.concat(
                        repository.findByUserIdAndTicker(userId, position.getTicker()).stream()
                                .filter(lot -> !lot.getId().equals(position.getId())),
                        deletedId == null ? Stream.of(position) : Stream.empty())
                .toList();
        final LotAllocation allocation = LotAllocation.of(lots, sales);
        if (allocation.unsold() != null) {
            throw unprocessable("Sales of %s on %s would exceed the bonds bought by that date: change or delete the sale first"
                    .formatted(position.getTicker(), allocation.unsold().getSellDate()));
        }
        return deletedId == null ? allocation.remaining(position) : 0;
    }

    /**
     * Итог полного дохода по каждой валюте номинала, в порядке облигаций. Облигации нет в системе - её валюта
     * неизвестна, она не входит ни в один итог (итог всего портфеля из-за неё неполный).
     */
    private static List<IncomeModel> incomeTotals(final Collection<BondIncome> incomes,
                                                  final BigDecimal taxRatePercent) {
        final Set<String> currencies = incomes.stream()
                .map(BondIncome::currency)
                .filter(Objects::nonNull)
                .map(PortfolioUseCaseImpl::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return currencies.stream()
                .map(currency -> IncomeAggregator.of(currency, incomes.stream()
                        .filter(income -> income.currency() != null && normalize(income.currency()).equals(currency))
                        .toList(), false, taxRatePercent))
                .filter(Objects::nonNull)
                .toList();
    }

    private static String normalize(final String currency) {
        return currency.toLowerCase();
    }

    /** НДФЛ - как в калькуляторе дохода: от 0 до 100 %, не задан - налог не считается */
    private static void validateTaxRate(final BigDecimal taxRatePercent) {
        if (taxRatePercent != null && (taxRatePercent.signum() < 0 || taxRatePercent.compareTo(HUNDRED) >= 0)) {
            throw unprocessable("taxRatePercent must be from 0 to 100");
        }
    }

    /**
     * @return дата покупки; не задана - сегодня
     */
    private LocalDate validate(final PositionRequestModel request) {
        if (request.quantity() == null || request.quantity() < 1 || request.quantity() > MAX_QUANTITY) {
            throw unprocessable("quantity must be from 1 to %d".formatted(MAX_QUANTITY));
        }
        if (request.buyPrice() == null
                || request.buyPrice().compareTo(MIN_PRICE) < 0
                || request.buyPrice().compareTo(MAX_PRICE) > 0) {
            throw unprocessable("buyPrice must be from %s to %s %% of nominal".formatted(MIN_PRICE.toPlainString(),
                    MAX_PRICE.toPlainString()));
        }
        if (isNegative(request.aci()) || isNegative(request.commission())) {
            throw unprocessable("aci and commission must not be negative");
        }
        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        final LocalDate buyDate = Optional.ofNullable(request.buyDate()).orElse(today);
        if (buyDate.isAfter(today)) {
            throw unprocessable("buyDate must not be in the future");
        }
        return buyDate;
    }

    /** После погашения облигацию не купить; дата погашения неизвестна - не проверяется */
    private static void validateNotAfterMaturity(final LocalDate buyDate, final BondModel bond) {
        final LocalDateTime maturity = bond == null ? null : bond.getMaturityDate();
        if (maturity != null && buyDate.isAfter(maturity.toLocalDate())) {
            throw unprocessable("buyDate must not be after the maturity date %s".formatted(maturity.toLocalDate()));
        }
    }

    private static boolean isNegative(final BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    /**
     * Рублёвая сумма покупки - только у валютной облигации: у рублёвой она равна стоимости покупки. Облигации больше
     * нет в системе (валюта неизвестна) - сумма принимается.
     */
    private static void validateBuyAmountRub(final BigDecimal buyAmountRub, final String currency) {
        if (buyAmountRub != null && currency != null && RUB_CODES.contains(currency.toUpperCase())) {
            throw unprocessable("buyAmountRub is only for bonds in foreign currency");
        }
    }

    /** Валюта номинала облигации; null - облигации нет */
    private static String currency(final BondModel bond) {
        if (bond == null) {
            return null;
        }
        return Optional.ofNullable(bond.getPrice())
                .map(PriceModel::getNominal)
                .map(MoneyModel::getCurrency)
                .orElse(bond.getCurrency());
    }

    private PortfolioPosition find(final String userId, final Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.NOT_FOUND,
                        NOT_FOUND.formatted(id)));
    }

    private static GeneralUnprocessableEntityException unprocessable(final String message) {
        return new GeneralUnprocessableEntityException(ExceptionErrorCode.PORTFOLIO_INVALID, message);
    }

    private static String broker(final Map<Long, String> brokers, final PortfolioPosition position) {
        return position.getBrokerId() == null ? null : brokers.get(position.getBrokerId());
    }

    /**
     * Доход от цены непроданной части покупки: номинал × (текущая цена − цена покупки) × непроданное количество.
     * Номинал - текущий (после частичных погашений) и для покупки, и для текущей стоимости. Купоны и НКД - в полном
     * доходе строки облигации.
     */
    private PositionModel toModel(final PortfolioPosition position, final BondModel bond,
                                  final String broker, final int remaining, final RubRates rates) {
        final PriceModel price = bond == null ? null : bond.getPrice();
        final MoneyModel nominalMoney = price == null ? null : price.getNominal();
        final BigDecimal nominal = nominalMoney == null ? null : nominalMoney.getQuantity();
        final String currency = currency(bond);
        final BigDecimal currentPrice = price == null ? null : price.getPercentagePrice();
        final BigDecimal quantity = BigDecimal.valueOf(remaining);

        final BigDecimal buyAmount = amount(nominal, position.getBuyPrice(), quantity);
        final BigDecimal currentAmount = amount(nominal, currentPrice, quantity);
        final BigDecimal revenue = buyAmount == null || currentAmount == null
                ? null
                : currentAmount.subtract(buyAmount);
        final BigDecimal revenuePercent = currentPrice == null
                ? null
                : currentPrice.subtract(position.getBuyPrice()).multiply(HUNDRED)
                        .divide(position.getBuyPrice(), PERCENT_SCALE, RoundingMode.HALF_UP);

        // рубли: покупка - сколько заплачено по отчёту брокера (доля непроданного), не ввели - по курсу ЦБ на дату
        // покупки; сейчас - по курсу на сегодня; разница включает изменение курса
        final BigDecimal buyAmountInRub = position.getBuyAmountRub() != null
                ? position.getBuyAmountRub().multiply(quantity)
                        .divide(BigDecimal.valueOf(position.getQuantity()), MONEY_SCALE, RoundingMode.HALF_UP)
                : rates.toRub(buyAmount, currency, position.getBuyDate());
        final BigDecimal currentAmountInRub = rates.toRub(currentAmount, currency, null);
        final BigDecimal revenueInRub = buyAmountInRub == null || currentAmountInRub == null
                ? null
                : currentAmountInRub.subtract(buyAmountInRub);

        return new PositionModel(position.getId(), position.getTicker(),
                bond == null ? null : bond.getName(),
                bond == null ? null : bond.getIsin(),
                currency, position.getQuantity(), remaining, position.getBuyDate(), broker, nominal,
                position.getBuyPrice(), currentPrice, buyAmount, currentAmount, revenue, revenuePercent,
                position.getBuyAmountRub(), buyAmountInRub, currentAmountInRub, revenueInRub,
                yieldAtPrice(bond, position.getBuyPrice()), position.getAci(), position.getCommission(),
                position.getCreatedAt());
    }

    /**
     * Облигация с графиками; её уже нет в системе - пусто (покупка остаётся в портфеле без цены). Есть ли она,
     * проверяется заранее: исключение «облигации нет» из транзакционного метода пометило бы транзакцию на откат.
     */
    private Optional<BondModel> findWithSchedule(final String ticker) {
        return bondUseCase.getByTickers(List.of(ticker)).containsKey(ticker)
                ? Optional.of(bondUseCase.getWithSchedule(ticker))
                : Optional.empty();
    }

    private BigDecimal yieldAtPrice(final BondModel bond, final BigDecimal pricePercent) {
        return bond == null ? null : rounded(bondUseCase.getYieldToMaturityAtPrice(bond, pricePercent));
    }

    private BondMetrics metrics(final Map<String, BondModel> bonds) {
        return new BondMetrics() {
            @Override
            public BigDecimal couponRate(final String ticker) {
                return PortfolioUseCaseImpl.couponRate(bonds.get(ticker));
            }

            @Override
            public BigDecimal currentYieldToMaturity(final String ticker) {
                return Optional.ofNullable(bonds.get(ticker))
                        .map(BondModel::getCoupon)
                        .map(CouponModel::getYieldToMaturity)
                        .map(PortfolioUseCaseImpl::rounded)
                        .orElse(null);
            }

            @Override
            public BigDecimal yieldToMaturityAtPrice(final String ticker, final BigDecimal pricePercent) {
                return yieldAtPrice(bonds.get(ticker), pricePercent);
            }
        };
    }

    /**
     * Купон в % годовых от номинала. Купонная доходность облигации - годовые купоны к текущей цене, поэтому
     * к номиналу: доходность × текущая цена в % / 100.
     */
    private static BigDecimal couponRate(final BondModel bond) {
        if (bond == null || bond.getCoupon() == null || bond.getCoupon().getInterest() == null
                || bond.getPrice() == null || bond.getPrice().getPercentagePrice() == null) {
            return null;
        }
        return bond.getCoupon().getInterest().multiply(bond.getPrice().getPercentagePrice())
                .divide(HUNDRED, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal rounded(final BigDecimal percent) {
        return percent == null ? null : percent.setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal amount(final BigDecimal nominal, final BigDecimal percent, final BigDecimal quantity) {
        if (nominal == null || percent == null) {
            return null;
        }
        return nominal.multiply(percent).multiply(quantity).divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
