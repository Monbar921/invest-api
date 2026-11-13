package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Все покупки одной облигации одной строкой. Суммы - сложение сумм покупок по непроданному остатку, в валюте номинала.
 *
 * @param quantity           сколько облигаций не продано во всех покупках
 * @param brokers            разные брокеры покупок по имени; покупки без брокера не добавляют имени
 * @param firstBuyDate       самая ранняя дата покупки
 * @param lastBuyDate        самая поздняя дата покупки
 * @param buyPrice           средняя цена покупки, % от номинала, взвешенная по количеству
 * @param currentAmount      null - цены нет
 * @param revenue            null - цены нет
 * @param revenuePercent     доход в % к стоимости покупки; null - цены нет
 * @param buyAmountInRub     null - хотя бы у одной покупки нет рублёвой суммы
 * @param currentAmountInRub null - цены или курса нет
 * @param revenueInRub       null - нет одной из рублёвых сумм
 * @param couponRate         купон, % годовых от номинала; null - посчитать нельзя
 * @param yieldToMaturity    доходность к погашению по средней цене покупки (как если бы купить сегодня по ней,
 *                           с сегодняшним НКД), % годовых
 * @param currentYieldToMaturity доходность к погашению по текущей цене, % годовых
 * @param income             полный доход по облигации за всё время - с проданными частями; null - облигации нет
 * @param positions          покупки по дате
 */
public record PortfolioGroupModel(String ticker,
                                  String bondName,
                                  String isin,
                                  String currency,
                                  int quantity,
                                  List<String> brokers,
                                  LocalDate firstBuyDate,
                                  LocalDate lastBuyDate,
                                  BigDecimal nominal,
                                  BigDecimal buyPrice,
                                  BigDecimal currentPrice,
                                  BigDecimal buyAmount,
                                  BigDecimal currentAmount,
                                  BigDecimal revenue,
                                  BigDecimal revenuePercent,
                                  BigDecimal buyAmountInRub,
                                  BigDecimal currentAmountInRub,
                                  BigDecimal revenueInRub,
                                  BigDecimal couponRate,
                                  BigDecimal yieldToMaturity,
                                  BigDecimal currentYieldToMaturity,
                                  IncomeModel income,
                                  List<PositionModel> positions) {
}
