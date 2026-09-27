package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.annotation.Active;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.usecase.BondSyncUseCase;
import ru.invest.api.tinkoff.supplier.provider.TinkoffBondProvider;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Active
public class TinkoffBondSyncUseCaseImpl implements BondSyncUseCase {
    private final TinkoffBondProvider tinkoffBondProvider;
    private final TinkoffBondUseCase tinkoffBondUseCase;
    private final TinkoffBondCacheRepositoryUseCase tinkoffBondCacheRepositoryUseCase;

    @Override
    public void syncAll(final AuditModel audit) {
        final Map<String, BondModel> tinkoffBonds = tinkoffBondProvider.getAllBonds();

        if (MapUtils.isEmpty(tinkoffBonds)) {
            return;
        }

        try {
            tinkoffBondUseCase.save(tinkoffBonds, audit);
        } finally {
            tinkoffBondCacheRepositoryUseCase.evictAll();
        }
    }
}
