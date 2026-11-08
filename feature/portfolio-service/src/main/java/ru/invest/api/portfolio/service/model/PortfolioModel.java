package ru.invest.api.portfolio.service.model;

import java.util.List;

/**
 * Портфель пользователя: позиции по тикеру и дате покупки и итоги по валютам.
 */
public record PortfolioModel(List<PositionModel> positions, List<PortfolioTotalModel> totals) {
}
