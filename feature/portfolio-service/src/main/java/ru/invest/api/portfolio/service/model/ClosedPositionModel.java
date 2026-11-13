package ru.invest.api.portfolio.service.model;

import java.time.LocalDate;

/**
 * Закрытая позиция: все облигации проданы или погашены. Остаётся в истории с полным доходом.
 *
 * @param bondName     null - облигации уже нет в системе
 * @param currency     валюта номинала; null - облигации нет
 * @param quantity     сколько облигаций было куплено
 * @param firstBuyDate первая покупка
 * @param closeDate    последняя продажа или погашение
 * @param income       полный доход; null - облигации нет в системе
 */
public record ClosedPositionModel(String ticker,
                                  String bondName,
                                  String isin,
                                  String currency,
                                  int quantity,
                                  LocalDate firstBuyDate,
                                  LocalDate closeDate,
                                  IncomeModel income) {
}
