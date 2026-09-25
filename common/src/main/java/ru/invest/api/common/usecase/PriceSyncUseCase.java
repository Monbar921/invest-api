package ru.invest.api.common.usecase;

import ru.invest.api.common.model.AuditModel;

public interface PriceSyncUseCase {
    void syncAll(AuditModel audit);

    void syncByTicker(String ticker, AuditModel audit);
}
