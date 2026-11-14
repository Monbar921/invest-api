package ru.invest.api.imports.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Сделка импорта для предпросмотра и отчёта.
 *
 * @param price     % от номинала
 * @param currency  валюта номинала: в ней НКД и комиссия
 * @param tradeId   id созданной покупки или продажи
 */
public record ImportItemModel(Long id, int lineNo, ImportKind kind, LocalDate date, String ticker, String isin,
                              String figi, String name, Integer quantity, BigDecimal price, BigDecimal aci,
                              BigDecimal commission, String currency, BigDecimal amountRub, String broker, ImportItemStatus status,
                              String message, Long tradeId) {
}
