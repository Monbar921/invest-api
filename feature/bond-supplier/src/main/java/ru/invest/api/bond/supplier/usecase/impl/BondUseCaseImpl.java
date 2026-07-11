package ru.invest.api.bond.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.usecase.BondSortUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BondUseCaseImpl implements BondUseCase {
    private final TinkoffBondUseCase tinkoffBondUseCase;
    private final BondSortUseCase bondSortUseCase;

    @Override
    public List<BondModel> getForeignCurrencyBonds(final BondParametersModel bondParametersModel) {
        return bondSortUseCase.getFilteredBonds(bondParametersModel, tinkoffBondUseCase.getForeignCurrencyBonds(
                bondParametersModel));
    }

    @Override
    public List<BondModel> getRubbleCurrencyBonds(final BondParametersModel bondParametersModel) {
        return bondSortUseCase.getFilteredBonds(bondParametersModel, tinkoffBondUseCase.getRubbleCurrencyBonds(
                bondParametersModel));
    }
}
