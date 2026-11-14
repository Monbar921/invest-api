package ru.invest.api.dto.portfolio.imports;

/** Откуда сделки импорта */
public enum ImportSourceDto {
    /** Операции счёта в Т-Инвестициях по токену только на чтение */
    TINVEST_API,
    /** Свой шаблон CSV */
    CSV,
    /** Брокерский отчёт Сбербанка (HTML) */
    SBER_HTML
}
