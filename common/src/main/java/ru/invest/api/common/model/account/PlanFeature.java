package ru.invest.api.common.model.account;

/**
 * Фича, которую тариф может включать или нет. Состав по тарифам - в ru.invest.api.plans.
 */
public enum PlanFeature {
    /** Личная ссылка на календарь выплат (.ics) */
    CALENDAR_ICS,
    /** Импорт сделок от брокера: по токену T-Invest и из файла отчёта */
    BROKER_IMPORT
}
