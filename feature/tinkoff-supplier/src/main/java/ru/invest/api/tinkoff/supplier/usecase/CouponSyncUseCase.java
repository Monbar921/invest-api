package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.AuditModel;

public interface CouponSyncUseCase {
    void syncAll(AuditModel audit);
}
