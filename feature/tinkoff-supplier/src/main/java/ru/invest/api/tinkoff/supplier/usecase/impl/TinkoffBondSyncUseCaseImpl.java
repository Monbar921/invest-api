package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.annotation.Active;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.usecase.BondSyncUseCase;
import ru.invest.api.tinkoff.supplier.provider.TinkoffBondProvider;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondSyncRepositoryUseCase;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Active
public class TinkoffBondSyncUseCaseImpl implements BondSyncUseCase {
    private final TinkoffBondProvider tinkoffBondProvider;
    private final TinkoffBondSyncRepositoryUseCase tinkoffBondSyncRepositoryUseCase;

    @Override
    public void syncAll() {
        final Map<String, BondModel> tinkoffBonds = tinkoffBondProvider.getAllBonds();

        if (MapUtils.isEmpty(tinkoffBonds)) {
            return;
        }

        tinkoffBondSyncRepositoryUseCase.sync(tinkoffBonds);
    }
}
