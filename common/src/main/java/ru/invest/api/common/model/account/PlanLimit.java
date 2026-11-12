package ru.invest.api.common.model.account;

/**
 * Лимит тарифа: сколько чего может создать пользователь. Значения по тарифам - в ru.invest.api.plans.
 */
public enum PlanLimit {
    /** Правила уведомлений */
    ALERTS,
    /** Покупки в портфеле */
    PORTFOLIO_POSITIONS
}
