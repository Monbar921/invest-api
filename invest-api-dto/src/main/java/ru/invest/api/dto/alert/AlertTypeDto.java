package ru.invest.api.dto.alert;

/**
 * Тип правила. По датам - за days дней до события. По цене и доходности - порог threshold в направлении direction.
 */
public enum AlertTypeDto {
    COUPON_BEFORE,
    AMORTIZATION_BEFORE,
    OFFER_BEFORE,
    MATURITY_BEFORE,
    /** цена, % от номинала, дошла до уровня threshold (1..300) */
    PRICE_LEVEL,
    /** цена изменилась на threshold % (0,1..50) от цены при создании; один раз */
    PRICE_CHANGE,
    /** цена изменилась на threshold % (0,1..50) от закрытия прошлого торгового дня; не чаще раза в день */
    PRICE_DAILY_CHANGE,
    /** доходность к погашению, % годовых, дошла до уровня threshold (0..100) */
    YTM_LEVEL,
    /** доходность к оферте, % годовых, дошла до уровня threshold (0..100) */
    YTO_LEVEL
}
