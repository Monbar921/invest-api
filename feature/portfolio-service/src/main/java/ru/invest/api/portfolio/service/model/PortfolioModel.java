package ru.invest.api.portfolio.service.model;

import java.util.List;

/**
 * Портфель пользователя: покупки, сгруппированные по облигации (по тикеру), итоги по валютам и весь портфель в рублях.
 *
 * @param summary null - портфель пуст или хотя бы у одной валюты нет рублёвых сумм (нет курса)
 */
public record PortfolioModel(List<PortfolioGroupModel> groups,
                             List<PortfolioTotalModel> totals,
                             PortfolioSummaryModel summary) {
}
