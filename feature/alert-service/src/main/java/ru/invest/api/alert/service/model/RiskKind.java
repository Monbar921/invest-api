package ru.invest.api.alert.service.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Риск по облигации портфеля, о котором предупреждает монитор риска.
 */
@Getter
@RequiredArgsConstructor
public enum RiskKind {
    /** АКРА или НКР понизили рейтинг эмитента или выпуска */
    RATING_DOWNGRADE("понижение кредитного рейтинга"),
    /** Мосбиржа приостановила торги выпуском */
    TRADING_SUSPENSION("приостановка торгов"),
    /** дефолт: в новости слово «дефолт» или рейтинг понижен до D, RD, SD */
    DEFAULT("дефолт"),
    /** технический дефолт */
    TECHNICAL_DEFAULT("технический дефолт");

    /** Как называть в тексте уведомления */
    private final String title;
}
