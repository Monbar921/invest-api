package ru.invest.api.common.usecase;

import ru.invest.api.common.model.AuditModel;

public interface BondSyncUseCase {
    void syncAll(AuditModel audit);
}
