package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.AuditModel;

import java.util.Set;

public interface CouponInterestUseCase {
    /**
     * Пересчитывает годовую доходность купона (coupon.interest) относительно текущей цены облигации.
     *
     * @param uids uid облигаций, у которых обновилась цена
     */
    void recalculate(Set<String> uids, AuditModel audit);
}
