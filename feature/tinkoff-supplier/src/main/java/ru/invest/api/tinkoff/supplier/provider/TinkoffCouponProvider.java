package ru.invest.api.tinkoff.supplier.provider;

import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.CouponDataModel;

import java.util.List;

public interface TinkoffCouponProvider {
    List<CouponDataModel> getCouponData(String uid, String ticker, AuditModel audit);
}
