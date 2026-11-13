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
import ru.invest.api.common.repository.PortfolioPositionRepository;
import ru.invest.api.common.repository.PortfolioSaleRepository;
import ru.invest.api.portfolio.service.model.SaleRequestModel;
import ru.invest.api.portfolio.service.model.TradeModel;
import ru.invest.api.portfolio.service.model.TradeType;
import ru.invest.api.portfolio.service.usecase.PortfolioTradeUseCase;
import ru.invest.api.tinkoff.supplier.service.PriceConversionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PortfolioTradeUseCaseImpl implements PortfolioTradeUseCase {
    private static final String NOT_FOUND = "Portfolio sale %d not found";
    private static final int MAX_QUANTITY = 1_000_000;
    // продаж не больше, чем разумно ввести вручную; лимит тарифа - на покупки
    private static final int MAX_SALES = 1000;
    private static final BigDecimal MIN_PRICE = new BigDecimal("0.01");
    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(300);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final PortfolioSaleRepository saleRepository;
    private final PortfolioPositionRepository positionRepository;
    private final BrokerResolver brokerResolver;
    private final BondUseCase bondUseCase;
    private final DateTimeMapper dateTimeMapper;
    private final PriceConversionService priceConversionService;
    private final PortfolioIncomeCalculator incomeCalculator;

    @Override
    @Transactional(readOnly = true)
    public List<TradeModel> getTrades(final String userId) {
        final Map<String, List<PortfolioPosition>> lots = positionRepository
                .findByUserIdOrderByTickerAscBuyDateAscIdAsc(userId).stream()
                .collect(Collectors.groupingBy(PortfolioPosition::getTicker));
        final Map<String, List<PortfolioSale>> sales = saleRepository
                .findByUserIdOrderByTickerAscSellDateAscIdAsc(userId).stream()
                .collect(Collectors.groupingBy(PortfolioSale::getTicker));
        final Map<Long, String> brokers = brokerResolver.names(userId);
        final RubRates rates = new RubRates(priceConversionService);
        return lots.entrySet().stream()
                .flatMap(entry -> incomeCalculator.calculate(entry.getKey(), findWithSchedule(entry.getKey()),
                        entry.getValue(), sales.getOrDefault(entry.getKey(), List.of()), brokers, rates)
                        .trades().stream())
                .sorted(Comparator.comparing(TradeModel::date).reversed()
                        .thenComparing(TradeModel::type, Comparator.reverseOrder())
                        .thenComparing(TradeModel::id, Comparator.nullsFirst(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    @Transactional
    public TradeModel createSale(final String userId, final SaleRequestModel request) {
        if (request.ticker() == null || request.ticker().isBlank()) {
            throw unprocessable("Выберите облигацию");
        }
        final LocalDate sellDate = validate(request);
        // 404, если такой облигации нет
        final BondModel bond = bondUseCase.getWithSchedule(request.ticker());
        validateBeforeMaturity(sellDate, bond);
        if (saleRepository.countByUserId(userId) >= MAX_SALES) {
            throw unprocessable("Продаж не может быть больше %d".formatted(MAX_SALES));
        }
        final PortfolioBroker broker = brokerResolver.resolve(userId, request.broker());
        final PortfolioSale sale = saleRepository.save(fill(new PortfolioSale()
                .setUserId(userId)
                .setTicker(bond.getTicker())
                .setCreatedAt(LocalDateTime.now(ZoneOffset.UTC)), request, sellDate, broker));
        return checkedTrade(userId, sale, bond);
    }

    @Override
    @Transactional
    public TradeModel updateSale(final String userId, final Long id, final SaleRequestModel request) {
        final PortfolioSale sale = find(userId, id);
        final LocalDate sellDate = validate(request);
        final BondModel bond = findWithSchedule(sale.getTicker());
        validateBeforeMaturity(sellDate, bond);
        final PortfolioBroker broker = brokerResolver.resolve(userId, request.broker());
        fill(sale, request, sellDate, broker).setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        saleRepository.save(sale);
        return checkedTrade(userId, sale, bond);
    }

    @Override
    @Transactional
    public void deleteSale(final String userId, final Long id) {
        // без продажи облигаций остаётся больше - остальным продажам хватает
        saleRepository.delete(find(userId, id));
    }

    private static PortfolioSale fill(final PortfolioSale sale, final SaleRequestModel request,
                                      final LocalDate sellDate, final PortfolioBroker broker) {
        return sale.setQuantity(request.quantity())
                .setSellPrice(request.sellPrice())
                .setSellDate(sellDate)
                .setAci(request.aci())
                .setCommission(request.commission())
                .setBrokerId(broker == null ? null : broker.getId());
    }

    /**
     * Проверяет, что всем продажам облигации хватает покупок на их даты (FIFO), и возвращает сделку продажи.
     *
     * @throws GeneralUnprocessableEntityException не хватает
     */
    private TradeModel checkedTrade(final String userId, final PortfolioSale sale, final BondModel bond) {
        final List<PortfolioPosition> lots = positionRepository.findByUserIdAndTicker(userId, sale.getTicker());
        final List<PortfolioSale> sales = saleRepository.findByUserIdAndTicker(userId, sale.getTicker());
        final LotAllocation allocation = LotAllocation.of(lots, sales);
        if (allocation.unsold() != null) {
            throw unprocessable("Не хватает облигаций %s: к %s куплено %d шт., а продано %d шт."
                    .formatted(sale.getTicker(), DATE.format(allocation.unsold().getSellDate()),
                            boughtBy(lots, allocation.unsold().getSellDate()),
                            soldBy(sales, allocation.unsold().getSellDate())));
        }
        return incomeCalculator.calculate(sale.getTicker(), bond, lots, sales, brokerResolver.names(userId),
                        new RubRates(priceConversionService))
                .trades().stream()
                .filter(trade -> trade.type() == TradeType.SALE && sale.getId().equals(trade.id()))
                .findFirst()
                .orElseThrow();
    }

    private static int boughtBy(final List<PortfolioPosition> lots, final LocalDate date) {
        return lots.stream().filter(lot -> !lot.getBuyDate().isAfter(date)).mapToInt(PortfolioPosition::getQuantity)
                .sum();
    }

    private static int soldBy(final List<PortfolioSale> sales, final LocalDate date) {
        return sales.stream().filter(sale -> !sale.getSellDate().isAfter(date)).mapToInt(PortfolioSale::getQuantity)
                .sum();
    }

    /**
     * @return дата продажи; не задана - сегодня
     */
    private LocalDate validate(final SaleRequestModel request) {
        if (request.quantity() == null || request.quantity() < 1 || request.quantity() > MAX_QUANTITY) {
            throw unprocessable("Количество - от 1 до %d".formatted(MAX_QUANTITY));
        }
        if (request.sellPrice() == null
                || request.sellPrice().compareTo(MIN_PRICE) < 0
                || request.sellPrice().compareTo(MAX_PRICE) > 0) {
            throw unprocessable("Цена продажи - от %s до %s %% номинала".formatted(MIN_PRICE.toPlainString(),
                    MAX_PRICE.toPlainString()));
        }
        if (isNegative(request.aci()) || isNegative(request.commission())) {
            throw unprocessable("НКД и комиссия не могут быть отрицательными");
        }
        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        final LocalDate sellDate = Optional.ofNullable(request.sellDate()).orElse(today);
        if (sellDate.isAfter(today)) {
            throw unprocessable("Дата продажи не может быть в будущем");
        }
        return sellDate;
    }

    /** В дату погашения и позже облигации не продают - их гасит эмитент (погашение портфель учитывает сам) */
    private static void validateBeforeMaturity(final LocalDate sellDate, final BondModel bond) {
        final LocalDateTime maturity = bond == null ? null : bond.getMaturityDate();
        if (maturity != null && !sellDate.isBefore(maturity.toLocalDate())) {
            throw unprocessable("Продать можно только до даты погашения %s: в эту дату облигация погашается сама"
                    .formatted(DATE.format(maturity.toLocalDate())));
        }
    }

    private static boolean isNegative(final BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    private PortfolioSale find(final String userId, final Long id) {
        return saleRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.NOT_FOUND,
                        NOT_FOUND.formatted(id)));
    }

    /**
     * Облигация с графиками; её уже нет в системе - null. Есть ли она, проверяется заранее: исключение «облигации нет»
     * из транзакционного метода пометило бы транзакцию на откат.
     */
    private BondModel findWithSchedule(final String ticker) {
        return bondUseCase.getByTickers(List.of(ticker)).containsKey(ticker)
                ? bondUseCase.getWithSchedule(ticker)
                : null;
    }

    private static GeneralUnprocessableEntityException unprocessable(final String message) {
        return new GeneralUnprocessableEntityException(ExceptionErrorCode.PORTFOLIO_INVALID, message);
    }
}
