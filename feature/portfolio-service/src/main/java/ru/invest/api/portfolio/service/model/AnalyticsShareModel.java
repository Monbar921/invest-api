package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Доля группы позиций в оценённой части портфеля.
 *
 * @param key        значение признака: id эмитента, сектор, ступень рейтинга, валюта, тип купона, год погашения;
 *                   null - «нет данных»
 * @param title      название для показа: у эмитента - короткое название, у рейтинга - ступень; иначе null
 * @param valueInRub стоимость позиций группы в рублях
 * @param share      доля, %, 2 знака; доли одного разреза в сумме - ровно 100
 * @param tickers    облигации группы по убыванию стоимости
 */
public record AnalyticsShareModel(String key,
                                  String title,
                                  BigDecimal valueInRub,
                                  BigDecimal share,
                                  List<String> tickers) {
}
