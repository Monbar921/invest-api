package ru.invest.api.dto.request.bond;

/**
 * Формат выгрузки списка облигаций.
 */
public enum BondExportFormatRequest {
    /** CSV для Excel: UTF-8 с BOM, разделитель «;», десятичная запятая */
    CSV,
    /** Книга Excel */
    XLSX
}
