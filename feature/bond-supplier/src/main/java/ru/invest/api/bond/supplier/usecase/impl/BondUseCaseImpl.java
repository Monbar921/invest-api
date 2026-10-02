package ru.invest.api.bond.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.usecase.BondSortUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BondUseCaseImpl implements BondUseCase {
    private final TinkoffBondCacheRepositoryUseCase tinkoffBondCacheRepositoryUseCase;
    private final TinkoffBondUseCase tinkoffBondUseCase;
    private final BondSortUseCase bondSortUseCase;

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
