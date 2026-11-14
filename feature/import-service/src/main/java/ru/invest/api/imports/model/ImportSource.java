package ru.invest.api.imports.model;

/**
 * Откуда сделки: T-Invest API по токену или файл отчёта брокера (формат - за интерфейсом
 * {@link ru.invest.api.imports.parser.ReportParser}).
 */
public enum ImportSource {
    /** Операции счёта в Т-Инвестициях по токену только на чтение */
    TINVEST_API,
    /** Свой шаблон CSV: дата, операция, ISIN или тикер, количество, цена в % номинала, НКД, комиссия */
    CSV,
    /** Брокерский отчёт Сбербанка (HTML): таблица сделок купли/продажи ценных бумаг */
    SBER_HTML
}
