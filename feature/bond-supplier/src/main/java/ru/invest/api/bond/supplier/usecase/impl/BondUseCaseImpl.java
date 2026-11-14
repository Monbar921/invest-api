package ru.invest.api.bond.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.invest.api.bond.supplier.model.BondScope;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.calculation.ProfitCalculationModel;
import ru.invest.api.common.model.calculation.ProfitCalculationRequestModel;
import ru.invest.api.common.model.candle.CandleInterval;
import ru.invest.api.common.model.candle.CandleModel;
import ru.invest.api.common.model.event.BondEventModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.usecase.BondEventUseCase;
import ru.invest.api.common.usecase.BondScreenerEnricher;
import ru.invest.api.common.usecase.BondSearchUseCase;
import ru.invest.api.common.usecase.CandleAggregationUseCase;
import ru.invest.api.common.usecase.BondSortUseCase;
import ru.invest.api.tinkoff.supplier.service.CouponCalculationService;
import ru.invest.api.tinkoff.supplier.service.ProfitCalculationService;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffAmortizationUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCandleUseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BondUseCaseImpl implements BondUseCase {
    // раньше этой даты свечей нет: «вся история» без даты начала
    private static final LocalDate HISTORY_START = LocalDate.of(1990, 1, 1);
    // прошедшие события тоже полезны: видно, был ли недавно купон и как изменился его размер
    private static final int PAST_EVENT_MONTHS = 3;
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final int PRICE_SCALE = 10;

    private final TinkoffBondCacheRepositoryUseCase tinkoffBondCacheRepositoryUseCase;
    private final TinkoffBondUseCase tinkoffBondUseCase;
    private final BondSortUseCase bondSortUseCase;
    private final BondSearchUseCase bondSearchUseCase;
    private final TinkoffAmortizationUseCase tinkoffAmortizationUseCase;
    private final ProfitCalculationService profitCalculationService;
    private final CouponCalculationService couponCalculationService;
    private final TinkoffCandleUseCase tinkoffCandleUseCase;
    private final CandleAggregationUseCase candleAggregationUseCase;
    private final BondEventUseCase bondEventUseCase;
    private final DateTimeMapper dateTimeMapper;
    private final List<BondScreenerEnricher> screenerEnrichers;

    @Override
    public Page<BondModel> getForeignCurrencyBondsPage(final BondParametersModel bondParametersModel, final Pageable pageable) {
        return toPage(tinkoffBondCacheRepositoryUseCase.getForeignCurrencyBonds(), bondParametersModel, pageable);
    }

    @Override
    public Page<BondModel> getRubbleCurrencyBondsPage(final BondParametersModel bondParametersModel, final Pageable pageable) {
        return toPage(tinkoffBondCacheRepositoryUseCase.getRubbleCurrencyBonds(), bondParametersModel, pageable);
    }

    @Override
    public Page<BondModel> getAllPage(final BondParametersModel bondParametersModel, final Pageable pageable) {
        return toPage(tinkoffBondCacheRepositoryUseCase.getBonds(), bondParametersModel, pageable);
    }

    @Override
    public BondModel getByTicker(final String ticker) {
        final BondModel bond = tinkoffBondUseCase.findDetailedByTicker(ticker);
        // карточка читается мимо кэша - данные скринера добавляем так же, как при загрузке кэша, и с подробностями
        screenerEnrichers.forEach(enricher -> enricher.enrich(List.of(bond), true));
        return bond;
    }

    @Override
    public List<BondModel> getAllFiltered(final BondScope scope, final BondParametersModel bondParametersModel) {
        final Map<String, BondModel> cachedBonds = switch (scope) {
            case ALL -> tinkoffBondCacheRepositoryUseCase.getBonds();
            case LOCAL -> tinkoffBondCacheRepositoryUseCase.getRubbleCurrencyBonds();
            case FOREIGN -> tinkoffBondCacheRepositoryUseCase.getForeignCurrencyBonds();
        };
        return MapUtils.isEmpty(cachedBonds)
                ? List.of()
                : bondSortUseCase.getAllFilteredBonds(bondParametersModel, List.copyOf(cachedBonds.values()));
    }

    @Override
    public List<String> getSectors() {
        return tinkoffBondCacheRepositoryUseCase.getBonds().values().stream()
                .map(BondModel::getSector)
                .filter(StringUtils::isNotBlank)
                .map(sector -> sector.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public Map<String, BondModel> getByTickers(final Collection<String> tickers) {
        return tinkoffBondUseCase.findByTickers(tickers).stream()
                .collect(Collectors.toMap(BondModel::getTicker, Function.identity(), (first, second) -> first));
    }

    @Override
    public List<BondModel> search(final String query, final int limit) {
        final Map<String, BondModel> cachedBonds = tinkoffBondCacheRepositoryUseCase.getBonds();
        return MapUtils.isEmpty(cachedBonds) ? List.of() : bondSearchUseCase.search(cachedBonds.values(), query, limit);
    }

    @Override
    public ProfitCalculationModel calculateProfit(final String ticker, final ProfitCalculationRequestModel request) {
        // карточка с графиком купонов; будущие погашения номинала карточка не грузит - добираем отдельно
        final BondModel bond = tinkoffBondUseCase.findDetailedByTicker(ticker);
        bond.setAmortizations(tinkoffAmortizationUseCase.findByUids(Set.of(bond.getUid()))
                .getOrDefault(bond.getUid(), List.of()));

        return profitCalculationService.calculate(bond, Optional.ofNullable(request).orElseGet(ProfitCalculationRequestModel::new));
    }

    @Override
    public List<BondEventModel> getEvents(final String ticker) {
        return bondEventUseCase.getEvents(getWithSchedule(ticker),
                dateTimeMapper.getNow().toLocalDate().minusMonths(PAST_EVENT_MONTHS));
    }

    @Override
    public BigDecimal getYieldToMaturityAtPrice(final BondModel bond, final BigDecimal pricePercent) {
        final PriceModel price = bond == null ? null : bond.getPrice();
        if (price == null || pricePercent == null || bond.getCoupon() == null
                || price.getNominal() == null || price.getNominal().getQuantity() == null
                || price.getCurrent() == null || price.getCurrent().getCurrency() == null
                || !price.getCurrent().getCurrency().equalsIgnoreCase(price.getNominal().getCurrency())) {
            return null;
        }
        // та же облигация, но цена - заданная: расчёт берёт цену покупки из current, НКД - сегодняшний
        final MoneyModel current = new MoneyModel()
                .setCurrency(price.getCurrent().getCurrency())
                .setQuantity(price.getNominal().getQuantity().multiply(pricePercent)
                        .divide(PERCENT, PRICE_SCALE, RoundingMode.HALF_UP));
        final BondModel atPrice = new BondModel()
                .setTicker(bond.getTicker())
                .setCurrency(bond.getCurrency())
                .setMaturityDate(bond.getMaturityDate())
                .setOfferDate(bond.getOfferDate())
                .setAmortizationFlag(bond.isAmortizationFlag())
                .setAmortizations(bond.getAmortizations())
                .setCoupon(bond.getCoupon())
                .setPrice(new PriceModel()
                        .setNominal(price.getNominal())
                        .setCurrent(current)
                        .setAci(price.getAci())
                        .setPercentagePrice(pricePercent));
        return couponCalculationService.calculateYieldToMaturity(bond.getCoupon(), atPrice);
    }

    @Override
    public BondModel getWithSchedule(final String ticker) {
        final BondModel bond = tinkoffBondUseCase.findDetailedByTicker(ticker);
        bond.setAmortizations(tinkoffAmortizationUseCase.findByUids(Set.of(bond.getUid()))
                .getOrDefault(bond.getUid(), List.of()));
        return bond;
    }

    @Override
    public List<CandleModel> getCandles(final String ticker, final CandleInterval interval, final LocalDate from,
                                        final LocalDate to) {
        final BondModel bond = tinkoffBondUseCase.findByTicker(ticker);
        final LocalDate start = Optional.ofNullable(from).orElse(HISTORY_START);
        final LocalDate end = Optional.ofNullable(to).orElseGet(() -> dateTimeMapper.getNow().toLocalDate());
        final List<CandleModel> daily = withCurrentPrice(tinkoffCandleUseCase.find(bond.getUid(), start, end),
                bond.getPrice(), start, end);
        return candleAggregationUseCase.aggregate(daily, interval);
    }

    /**
     * Последняя точка графика - текущая цена из синхронизации цен, та же, что в карточке. Свечи грузятся раз в сутки,
     * цена - чаще, поэтому без этого график отставал бы от карточки. Свеча на дату цены получает её как цену закрытия,
     * а если такой свечи ещё нет - добавляется точка из одной цены. Цена старше последней свечи график не меняет;
     * без свечей графика нет, и одна точка из цены его не заменяет.
     */
    private List<CandleModel> withCurrentPrice(final List<CandleModel> daily, final PriceModel price,
                                               final LocalDate from, final LocalDate to) {
        final BigDecimal current = Optional.ofNullable(price).map(PriceModel::getPercentagePrice).orElse(null);
        final LocalDate priceDate = Optional.ofNullable(price)
                .map(model -> Optional.ofNullable(model.getUpdated()).orElse(model.getCreated()))
                .map(AuditModel::getCommittedAt)
                .map(LocalDateTime::toLocalDate)
                .orElse(null);
        if (daily.isEmpty() || current == null || priceDate == null || priceDate.isBefore(from) || priceDate.isAfter(to)) {
            return daily;
        }

        final CandleModel last = daily.getLast();
        if (last.getDate().isAfter(priceDate)) {
            return daily;
        }

        final List<CandleModel> result = new ArrayList<>(daily);
        if (last.getDate().isEqual(priceDate)) {
            result.set(result.size() - 1, new CandleModel()
                    .setDate(priceDate)
                    .setOpen(last.getOpen())
                    .setHigh(last.getHigh().max(current))
                    .setLow(last.getLow().min(current))
                    .setClose(current)
                    .setVolume(last.getVolume()));
        } else {
            result.add(new CandleModel()
                    .setDate(priceDate)
                    .setOpen(current)
                    .setHigh(current)
                    .setLow(current)
                    .setClose(current)
                    .setVolume(0L));
        }
        return result;
    }

    // все облигации лежат в кэше: фильтруем и сортируем всю выборку в памяти, страница - срез списка
    private Page<BondModel> toPage(final Map<String, BondModel> cachedBonds, final BondParametersModel bondParametersModel,
                                   final Pageable pageable) {
        final List<BondModel> bonds = MapUtils.isEmpty(cachedBonds)
                ? List.of()
                : bondSortUseCase.getAllFilteredBonds(bondParametersModel, List.copyOf(cachedBonds.values()));

        final int from = (int) Math.min(pageable.getOffset(), bonds.size());
        final int to = Math.min(from + pageable.getPageSize(), bonds.size());

        return new PageImpl<>(bonds.subList(from, to), pageable, bonds.size());
    }
}
