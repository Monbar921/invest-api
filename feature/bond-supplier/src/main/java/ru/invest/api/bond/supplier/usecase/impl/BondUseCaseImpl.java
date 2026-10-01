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
import ru.invest.api.common.usecase.BondFilterUseCase;
import ru.invest.api.common.usecase.BondSortUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BondUseCaseImpl implements BondUseCase {
    private final TinkoffBondCacheRepositoryUseCase tinkoffBondCacheRepositoryUseCase;
    private final TinkoffBondUseCase tinkoffBondUseCase;
    private final BondSortUseCase bondSortUseCase;
    private final BondFilterUseCase bondFilterUseCase;

    @Override
    public List<BondModel> getForeignCurrencyBonds(final BondParametersModel bondParametersModel) {
        return bondSortUseCase.getFilteredBonds(bondParametersModel,
                filter(tinkoffBondCacheRepositoryUseCase.getForeignCurrencyBonds(), bondParametersModel));
    }

    @Override
    public List<BondModel> getRubbleCurrencyBonds(final BondParametersModel bondParametersModel) {
        return bondSortUseCase.getFilteredBonds(bondParametersModel,
                filter(tinkoffBondCacheRepositoryUseCase.getRubbleCurrencyBonds(), bondParametersModel));
    }

    @Override
    public List<BondModel> getAll(final BondParametersModel bondParametersModel) {
        return bondSortUseCase.getFilteredBonds(bondParametersModel,
                filter(tinkoffBondCacheRepositoryUseCase.getBonds(), bondParametersModel));
    }

    @Override
    public Page<BondModel> getForeignCurrencyBondsPage(final BondParametersModel bondParametersModel, final Pageable pageable) {
        return toPage(bondSortUseCase.getAllFilteredBonds(bondParametersModel,
                filter(tinkoffBondCacheRepositoryUseCase.getForeignCurrencyBonds(), bondParametersModel)), pageable);
    }

    @Override
    public Page<BondModel> getRubbleCurrencyBondsPage(final BondParametersModel bondParametersModel, final Pageable pageable) {
        return toPage(bondSortUseCase.getAllFilteredBonds(bondParametersModel,
                filter(tinkoffBondCacheRepositoryUseCase.getRubbleCurrencyBonds(), bondParametersModel)), pageable);
    }

    @Override
    public Page<BondModel> getAllPage(final BondParametersModel bondParametersModel, final Pageable pageable) {
        return toPage(bondSortUseCase.getAllFilteredBonds(bondParametersModel,
                filter(tinkoffBondCacheRepositoryUseCase.getBonds(), bondParametersModel)), pageable);
    }

    @Override
    public BondModel getByTicker(final String ticker) {
        return tinkoffBondUseCase.findDetailedByTicker(ticker);
    }

    private List<BondModel> filter(final Map<String, BondModel> bonds, final BondParametersModel bondParametersModel) {
        if (MapUtils.isEmpty(bonds)) {
            return Collections.emptyList();
        }

        final List<BondModel> bondList = bonds
                .entrySet()
                .stream()
                .filter(Objects::nonNull)
                .map(Map.Entry::getValue)
                .toList();

        return bondFilterUseCase.filter(bondList, bondParametersModel);
    }

    // облигации уже отфильтрованы и отсортированы в памяти - страница это срез списка
    private Page<BondModel> toPage(final List<BondModel> bonds, final Pageable pageable) {
        final int from = (int) Math.min(pageable.getOffset(), bonds.size());
        final int to = Math.min(from + pageable.getPageSize(), bonds.size());

        return new PageImpl<>(bonds.subList(from, to), pageable, bonds.size());
    }
}
