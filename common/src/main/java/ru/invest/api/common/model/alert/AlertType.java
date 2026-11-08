package ru.invest.api.common.model.alert;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.invest.api.common.model.event.BondEventType;

/**
 * Тип правила уведомления.
 * <p>
 * По датам - за days дней до события облигации. По цене и доходности - порог threshold в направлении direction:
 * уровни (цена в % от номинала, доходность в % годовых) срабатывают при пересечении, изменения (в процентах
 * относительно цены) - при отклонении на threshold.
 */
@Getter
@RequiredArgsConstructor
public enum AlertType {
    COUPON_BEFORE(BondEventType.COUPON),
    AMORTIZATION_BEFORE(BondEventType.AMORTIZATION),
    OFFER_BEFORE(BondEventType.OFFER),
    MATURITY_BEFORE(BondEventType.MATURITY),

    /** цена (% от номинала) поднялась до threshold (UP) или опустилась до него (DOWN) */
    PRICE_LEVEL(null),
    /** цена отклонилась от цены при создании правила (base_value) на threshold %; срабатывает один раз */
    PRICE_CHANGE(null),
    /** цена отклонилась от закрытия прошлого торгового дня на threshold %; не чаще раза в день */
    PRICE_DAILY_CHANGE(null),
    /** доходность к погашению, % годовых, пересекла threshold */
    YTM_LEVEL(null),
    /** доходность к оферте, % годовых, пересекла threshold */
    YTO_LEVEL(null);

    /** Событие облигации, о котором предупреждает правило по дате; null - правило по цене или доходности */
    private final BondEventType eventType;

    public boolean isDateRule() {
        return eventType != null;
    }

    /** Порог - уровень значения, а не изменение: срабатывает при пересечении, с повторным взведением */
    public boolean isLevel() {
        return this == PRICE_LEVEL || this == YTM_LEVEL || this == YTO_LEVEL;
    }
}
