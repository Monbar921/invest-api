package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.annotation.Active;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.ShortProductModel;
import ru.invest.api.common.usecase.AmortizationSyncUseCase;
import ru.invest.api.tinkoff.supplier.provider.TinkoffAmortizationProvider;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffAmortizationUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Active
public class TinkoffAmortizationSyncUseCaseImpl implements AmortizationSyncUseCase {
    private static final int BATCH_SIZE = 100;

    private final TinkoffAmortizationProvider tinkoffAmortizationProvider;
    private final TinkoffAmortizationUseCase tinkoffAmortizationUseCase;
    private final TinkoffBondUseCase tinkoffBondUseCase;

    @Override
    public void syncAll(final AuditModel audit) {
        syncAmortizations(tinkoffAmortizationUseCase.getAmortizingBonds(), audit);
    }

    @Override
    public void syncByTicker(final String ticker, final AuditModel audit) {
        final BondModel bond = tinkoffBondUseCase.findByTicker(ticker);
        if (!bond.isAmortizationFlag()) {
            log.info("Bond {} has no amortization, skipped", ticker);
            return;
        }

        syncAmortizations(List.of(new ShortProductModel().setUid(bond.getUid()).setTicker(bond.getTicker())), audit);
    }

    private void syncAmortizations(final List<ShortProductModel> bonds, final AuditModel audit) {
        if (CollectionUtils.isEmpty(bonds)) {
            return;
        }

        log.info("Processing amortizations, total size is {}", bonds.size());
        ListUtils.partition(bonds, BATCH_SIZE)
                .forEach(batch -> tinkoffAmortizationUseCase.save(fetchAmortizations(batch, audit)));
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private Map<String, List<AmortizationModel>> fetchAmortizations(final List<ShortProductModel> batch, final AuditModel audit) {
        final Map<String, List<AmortizationModel>> amortizations = new HashMap<>(batch.size());

        for (final ShortProductModel bond : batch) {
            try {
                amortizations.put(bond.getUid(), tinkoffAmortizationProvider.getAmortizations(bond.getUid(), bond.getTicker(), audit));
            } catch (final RuntimeException e) {
                // график не получили - оставляем прежний, а не затираем его пустым
                log.warn("Failed to fetch amortizations for bond {}", bond.getTicker(), e);
            }
        }

        return amortizations;
    }
}
