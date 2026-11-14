package ru.invest.api.dto.account;

/**
 * Фича, которую тариф включает или нет.
 */
public enum PlanFeatureDto {
    /** Личная ссылка на календарь выплат (.ics) */
    CALENDAR_ICS,
    /** Импорт сделок от брокера: по токену T-Invest и из файла отчёта */
    BROKER_IMPORT
}
