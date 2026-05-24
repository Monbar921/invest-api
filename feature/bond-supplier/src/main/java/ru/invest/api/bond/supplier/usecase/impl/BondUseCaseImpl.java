package ru.invest.api.bond.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BondUseCaseImpl implements BondUseCase {
    private final TinkoffBondUseCase tinkoffBondUseCase;

    @Override
    public List<BondModel> getForeignCurrencyBonds(final BondParametersModel bondParametersModel) {
        final List<BondModel> foreignCurrencyBonds = tinkoffBondUseCase.getForeignCurrencyBonds();

        if(CollectionUtils.isEmpty(foreignCurrencyBonds)){
            return foreignCurrencyBonds;
        }

        final Integer batchLimit = Optional.ofNullable(bondParametersModel)
                .map(BondParametersModel::getBatchLimit)
                .orElse(null);

        if(batchLimit != null){
            return foreignCurrencyBonds
                    .stream()
                    .filter(Objects::nonNull)
                    .limit(batchLimit)
                    .toList();
        }

        return foreignCurrencyBonds;
    }
}
