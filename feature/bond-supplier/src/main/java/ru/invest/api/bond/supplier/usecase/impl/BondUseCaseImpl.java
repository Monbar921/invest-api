package ru.invest.api.bond.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.usecase.BondSortUseCase;
import ru.invest.api.common.usecase.BondSyncUseCase;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffBondDispatcher;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BondUseCaseImpl implements BondUseCase {
    private final TinkoffBondDispatcher tinkoffBondDispatcher;
    private final BondSortUseCase bondSortUseCase;

    private final List<BondSyncUseCase> bondSyncUseCases;

    @Override
    public List<BondModel> getForeignCurrencyBonds(final BondParametersModel bondParametersModel) {
        return bondSortUseCase.getFilteredBonds(bondParametersModel, tinkoffBondDispatcher.getForeignCurrencyBonds(
                bondParametersModel));
    }

    @Override
    public List<BondModel> getRubbleCurrencyBonds(final BondParametersModel bondParametersModel) {
        return bondSortUseCase.getFilteredBonds(bondParametersModel, tinkoffBondDispatcher.getRubbleCurrencyBonds(
                bondParametersModel));
    }

    @Override
    public void syncAll(final AuditModel auditModel) {
        bondSyncUseCases
                .stream()
                .findAny()
                .ifPresent(bondSyncUseCase -> bondSyncUseCase.syncAll(auditModel));
    }
}
