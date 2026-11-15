package ru.invest.api.portfolio.service.model;

/**
 * Позиция, которую нельзя оценить: её нет в долях, но она видна в аналитике отдельно.
 */
public record UnvaluedPositionModel(String ticker, String bondName, UnvaluedReason reason) {
}
