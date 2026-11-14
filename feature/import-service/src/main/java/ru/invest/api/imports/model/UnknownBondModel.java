package ru.invest.api.imports.model;

/**
 * Бумага из источника, которой нет среди облигаций системы: её сделки не импортируются, но показываются.
 *
 * @param trades сколько сделок с ней
 */
public record UnknownBondModel(String isin, String figi, String ticker, String name, int trades) {
}
