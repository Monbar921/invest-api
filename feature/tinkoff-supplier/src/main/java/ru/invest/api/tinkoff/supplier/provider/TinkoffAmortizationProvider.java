package ru.invest.api.tinkoff.supplier.provider;

import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.AuditModel;

import java.util.List;

public interface TinkoffAmortizationProvider {
    /**
     * Будущие погашения номинала облигации (частичные и итоговое) с известным размером выплаты.
     */
    List<AmortizationModel> getAmortizations(String uid, String ticker, AuditModel audit);
}
