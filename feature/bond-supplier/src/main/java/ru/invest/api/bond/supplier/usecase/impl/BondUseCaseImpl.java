package ru.invest.api.bond.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.calculation.ProfitCalculationModel;
import ru.invest.api.common.model.calculation.ProfitCalculationRequestModel;
import ru.invest.api.common.model.candle.CandleInterval;
import ru.invest.api.common.model.candle.CandleModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.usecase.BondSearchUseCase;
import ru.invest.api.common.usecase.CandleAggregationUseCase;
import ru.invest.api.common.usecase.BondSortUseCase;
import ru.invest.api.tinkoff.supplier.service.ProfitCalculationService;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffAmortizationUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCandleUseCase;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BondUseCaseImpl implements BondUseCase {
    // раньше этой даты свечей нет: «вся история» без даты начала
    private static final LocalDate HISTORY_START = LocalDate.of(1990, 1, 1);

    private final TinkoffBondCacheRepositoryUseCase tinkoffBondCacheRepositoryUseCase;
    private final TinkoffBondUseCase tinkoffBondUseCase;
    private final BondSortUseCase bondSortUseCase;
    private final BondSearchUseCase bondSearchUseCase;
    private final TinkoffAmortizationUseCase tinkoffAmortizationUseCase;
    private final ProfitCalculationService profitCalculationService;
    private final TinkoffCandleUseCase tinkoffCandleUseCase;
    private final CandleAggregationUseCase candleAggregationUseCase;
    private final DateTimeMapper dateTimeMapper;

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
        return tinkoffBondUseCase.findDetailedByTicker(ticker);
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
    public List<CandleModel> getCandles(final String ticker, final CandleInterval interval, final LocalDate from,
                                        final LocalDate to) {
        final BondModel bond = tinkoffBondUseCase.findByTicker(ticker);
        final List<CandleModel> daily = tinkoffCandleUseCase.find(bond.getUid(),
                Optional.ofNullable(from).orElse(HISTORY_START),
                Optional.ofNullable(to).orElseGet(() -> dateTimeMapper.getNow().toLocalDate()));
        return candleAggregationUseCase.aggregate(daily, interval);
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
