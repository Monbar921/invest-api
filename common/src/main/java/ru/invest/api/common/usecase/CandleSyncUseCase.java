package ru.invest.api.common.usecase;

import ru.invest.api.common.model.AuditModel;

/**
 * Синхронизация дневных свечей цены облигаций: в первый раз - вся история, дальше - с последней сохранённой свечи.
 */
public interface CandleSyncUseCase {
    void syncAll(AuditModel audit);

    void syncByTicker(String ticker, AuditModel audit);
}
