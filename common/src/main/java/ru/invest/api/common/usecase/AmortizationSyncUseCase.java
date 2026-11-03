package ru.invest.api.common.usecase;

import ru.invest.api.common.model.AuditModel;

/**
 * Синхронизация графика амортизации (частичных погашений номинала) облигаций с амортизацией.
 */
public interface AmortizationSyncUseCase {
    void syncAll(AuditModel audit);

    void syncByTicker(String ticker, AuditModel audit);
}
