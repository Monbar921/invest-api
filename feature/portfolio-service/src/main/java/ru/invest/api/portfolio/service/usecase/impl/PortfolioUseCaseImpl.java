package ru.invest.api.portfolio.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.PortfolioBroker;
import ru.invest.api.common.entity.PortfolioPosition;
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
import ru.invest.api.common.usecase.EntitlementUseCase;
import ru.invest.api.portfolio.service.model.BrokerModel;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioUseCaseImpl implements PortfolioUseCase {
    private static final String NOT_FOUND = "Portfolio position %d not found";
    private static final int MAX_BROKERS = 50;
    private static final int MAX_QUANTITY = 1_000_000;
    // цена покупки в % от номинала; верхний предел - как у порога цены в уведомлениях
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(300);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final Set<String> RUB_CODES = Set.of("RUB", "RUR");

    private final PortfolioPositionRepository repository;
    private final PortfolioBrokerRepository brokerRepository;
    private final BondUseCase bondUseCase;
    private final DateTimeMapper dateTimeMapper;
    private final PriceConversionService priceConversionService;
    private final EntitlementUseCase entitlementUseCase;

    @Override
    @Transactional(readOnly = true)
    public PortfolioModel getPortfolio(final String userId) {
        final List<PortfolioPosition> positions = repository.findByUserIdOrderByTickerAscBuyDateAscIdAsc(userId);
        // с графиком купонов и амортизации - для купона и доходности к погашению по цене покупки
        final Map<String, BondModel> bonds = new HashMap<>();
        positions.stream().map(PortfolioPosition::getTicker).distinct()
                .forEach(ticker -> findWithSchedule(ticker).ifPresent(bond -> bonds.put(ticker, bond)));
        final Map<Long, String> brokers = brokerRepository.findByUserIdOrderByNameAsc(userId).stream()
                .collect(Collectors.toMap(PortfolioBroker::getId, PortfolioBroker::getName));
        final RubRates rates = new RubRates();
        final List<PositionModel> models = positions.stream()
                .map(position -> toModel(position, bonds.get(position.getTicker()),
                        position.getBrokerId() == null ? null : brokers.get(position.getBrokerId()), rates))
                .toList();
        final List<PortfolioTotalModel> totals = PortfolioAggregator.totals(models);
        return new PortfolioModel(PortfolioAggregator.groups(models, metrics(bonds)), totals,
                PortfolioAggregator.summary(totals));
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
        final PortfolioBroker broker = resolveBroker(userId, request.broker());
        final PortfolioPosition position = repository.save(new PortfolioPosition()
                .setUserId(userId)
                .setTicker(bond.getTicker())
                .setQuantity(request.quantity())
                .setBuyPrice(request.buyPrice())
                .setBuyDate(buyDate)
                .setBuyAmountRub(request.buyAmountRub())
                .setBrokerId(broker == null ? null : broker.getId())
                .setCreatedAt(LocalDateTime.now(ZoneOffset.UTC)));
        return toModel(position, bond, broker == null ? null : broker.getName(), new RubRates());
    }

    @Override
    @Transactional
    public PositionModel update(final String userId, final Long id, final PositionRequestModel request) {
        final PortfolioPosition position = find(userId, id);
        final LocalDate buyDate = validate(request);
        final BondModel bond = findWithSchedule(position.getTicker()).orElse(null);
        validateBuyAmountRub(request.buyAmountRub(), currency(bond));
        final PortfolioBroker broker = resolveBroker(userId, request.broker());
        position.setQuantity(request.quantity())
                .setBuyPrice(request.buyPrice())
                .setBuyDate(buyDate)
                .setBuyAmountRub(request.buyAmountRub())
                .setBrokerId(broker == null ? null : broker.getId())
                .setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        repository.save(position);
        return toModel(position, bond, broker == null ? null : broker.getName(), new RubRates());
    }

    @Override
    @Transactional
    public void delete(final String userId, final Long id) {
        repository.delete(find(userId, id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BrokerModel> getBrokers(final String userId) {
        return brokerRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(broker -> new BrokerModel(broker.getId(), broker.getName()))
                .toList();
    }

    /**
     * Брокер по имени без учёта регистра и пробелов по краям; такого у пользователя ещё нет - создаётся и дальше
     * доступен для других покупок. Пустое имя - без брокера.
     */
    private PortfolioBroker resolveBroker(final String userId, final String name) {
        final String trimmed = name == null ? "" : name.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return brokerRepository.findByUserIdAndNameIgnoreCase(userId, trimmed).orElseGet(() -> {
            if (brokerRepository.countByUserId(userId) >= MAX_BROKERS) {
                throw unprocessable("No more than %d brokers per user".formatted(MAX_BROKERS));
            }
            return brokerRepository.save(new PortfolioBroker()
                    .setUserId(userId)
                    .setName(trimmed)
                    .setCreatedAt(LocalDateTime.now(ZoneOffset.UTC)));
        });
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
        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        final LocalDate buyDate = Optional.ofNullable(request.buyDate()).orElse(today);
        if (buyDate.isAfter(today)) {
            throw unprocessable("buyDate must not be in the future");
        }
        return buyDate;
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

    /**
     * Доход - только от изменения цены: номинал × (текущая цена − цена покупки) × количество. Номинал - текущий
     * (после частичных погашений) и для покупки, и для текущей стоимости. Купоны и НКД не учитываются.
     */
    private PositionModel toModel(final PortfolioPosition position, final BondModel bond,
                                  final String broker, final RubRates rates) {
        final PriceModel price = bond == null ? null : bond.getPrice();
        final MoneyModel nominalMoney = price == null ? null : price.getNominal();
        final BigDecimal nominal = nominalMoney == null ? null : nominalMoney.getQuantity();
        final String currency = currency(bond);
        final BigDecimal currentPrice = price == null ? null : price.getPercentagePrice();
        final BigDecimal quantity = BigDecimal.valueOf(position.getQuantity());

        final BigDecimal buyAmount = amount(nominal, position.getBuyPrice(), quantity);
        final BigDecimal currentAmount = amount(nominal, currentPrice, quantity);
        final BigDecimal revenue = buyAmount == null || currentAmount == null
                ? null
                : currentAmount.subtract(buyAmount);
        final BigDecimal revenuePercent = currentPrice == null
                ? null
                : currentPrice.subtract(position.getBuyPrice()).multiply(HUNDRED)
                        .divide(position.getBuyPrice(), PERCENT_SCALE, RoundingMode.HALF_UP);

        // рубли: покупка - сколько заплачено по отчёту брокера, не ввели - по курсу ЦБ на дату покупки; сейчас - по
        // курсу на сегодня; разница включает изменение курса
        final BigDecimal buyAmountInRub = position.getBuyAmountRub() != null
                ? position.getBuyAmountRub()
                : rates.toRub(buyAmount, currency, position.getBuyDate());
        final BigDecimal currentAmountInRub = rates.toRub(currentAmount, currency, null);
        final BigDecimal revenueInRub = buyAmountInRub == null || currentAmountInRub == null
                ? null
                : currentAmountInRub.subtract(buyAmountInRub);

        return new PositionModel(position.getId(), position.getTicker(),
                bond == null ? null : bond.getName(),
                bond == null ? null : bond.getIsin(),
                currency, position.getQuantity(), position.getBuyDate(), broker, nominal, position.getBuyPrice(),
                currentPrice, buyAmount, currentAmount, revenue, revenuePercent,
                position.getBuyAmountRub(), buyAmountInRub, currentAmountInRub, revenueInRub,
                yieldAtPrice(bond, position.getBuyPrice()), position.getCreatedAt());
    }

    /** Облигация с графиками; её уже нет в системе - пусто (покупка остаётся в портфеле без цены) */
    private Optional<BondModel> findWithSchedule(final String ticker) {
        try {
            return Optional.of(bondUseCase.getWithSchedule(ticker));
        } catch (final GeneralNotFoundEntityException e) {
            return Optional.empty();
        }
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

    /**
     * Курсы к рублю на один запрос портфеля: каждая пара «валюта, дата» запрашивается один раз, в том числе неудачно.
     * Дата null - курс на сегодня (ЦБ, иначе budget.org), иначе - курс ЦБ на эту дату.
     */
    private final class RubRates {
        private final Map<String, Optional<BigDecimal>> rates = new HashMap<>();

        BigDecimal toRub(final BigDecimal amount, final String currency, final LocalDate date) {
            if (amount == null || currency == null) {
                return null;
            }
            final String code = currency.toUpperCase();
            return rates.computeIfAbsent(code + "@" + date, key -> Optional.ofNullable(date == null
                            ? priceConversionService.getRubRate(code)
                            : priceConversionService.getRubRate(code, date)))
                    .map(rate -> amount.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                    .orElse(null);
        }
    }
}
