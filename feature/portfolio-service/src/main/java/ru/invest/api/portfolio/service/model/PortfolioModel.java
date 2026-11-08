package ru.invest.api.portfolio.service.model;

import java.util.List;

/**
 * Портфель пользователя: покупки, сгруппированные по облигации (по тикеру), и итоги по валютам.
 */
public record PortfolioModel(List<PortfolioGroupModel> groups, List<PortfolioTotalModel> totals) {
}
