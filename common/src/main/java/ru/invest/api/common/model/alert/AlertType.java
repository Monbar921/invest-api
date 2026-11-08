package ru.invest.api.common.model.alert;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.invest.api.common.model.event.BondEventType;

/**
 * Тип правила уведомления. Правила по датам срабатывают за days дней до события облигации.
 */
@Getter
@RequiredArgsConstructor
public enum AlertType {
    COUPON_BEFORE(BondEventType.COUPON),
    AMORTIZATION_BEFORE(BondEventType.AMORTIZATION),
    OFFER_BEFORE(BondEventType.OFFER),
    MATURITY_BEFORE(BondEventType.MATURITY);

    /** Событие облигации, о котором предупреждает правило */
    private final BondEventType eventType;
}
