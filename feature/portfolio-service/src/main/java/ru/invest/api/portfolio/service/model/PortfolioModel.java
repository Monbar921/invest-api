package ru.invest.api.portfolio.service.model;

import java.util.List;

/**
 * Портфель пользователя: покупки, сгруппированные по облигации (по тикеру), итоги по валютам и весь портфель в рублях.
 *
 * @param groups         открытые позиции: облигации, которые ещё не проданы и не погашены целиком
 * @param closed         закрытые позиции: всё продано или погашено - история с полным доходом
 * @param totals         итоги открытых позиций по валютам: доход от цены
 * @param summary        null - портфель пуст или хотя бы у одной валюты нет рублёвых сумм (нет курса)
 * @param incomeTotals   полный доход по валютам номинала - открытые и закрытые позиции
 * @param income         полный доход всего портфеля в рублях, XIRR портфеля; null - нечего считать
 */
public record PortfolioModel(List<PortfolioGroupModel> groups,
                             List<PortfolioTotalModel> totals,
                             PortfolioSummaryModel summary,
                             List<ClosedPositionModel> closed,
                             List<IncomeModel> incomeTotals,
                             IncomeModel income) {
}
