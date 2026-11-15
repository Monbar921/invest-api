package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Структура и риск открытых позиций портфеля. Доли - от стоимости оценённых позиций в рублях; позиция без
 * значения признака попадает в группу «нет данных» (key = null), позиция без оценки - в unvalued.
 *
 * @param valueInRub          стоимость оценённых позиций в рублях; null - оценённых нет
 * @param bonds               сколько облигаций (тикеров) в открытых позициях, с неоценёнными
 * @param unvalued            позиции, которые нельзя оценить в рублях
 * @param issuers             по эмитентам, по убыванию доли; «нет данных» - в конце
 * @param sectors             по секторам T-Invest
 * @param ratings             по ступени рейтинга, от лучшей к худшей
 * @param currencies          по валюте номинала
 * @param couponTypes         FIXED / FLOATING
 * @param maturities          лестница погашений: по году даты погашения, по возрастанию
 * @param yieldToMaturity     средневзвешенная доходность к погашению по текущей цене, % годовых
 * @param durationMacaulay    средневзвешенная дюрация Маколея, лет
 * @param durationModified    средневзвешенная модифицированная дюрация, лет
 * @param largestIssuer       крупнейший эмитент (концентрация); null - ни у одной позиции эмитент не известен
 * @param rateSensitivity     чувствительность к ставке; null - нет ни одной облигации с дюрацией
 */
public record PortfolioAnalyticsModel(BigDecimal valueInRub,
                                      int bonds,
                                      List<UnvaluedPositionModel> unvalued,
                                      List<AnalyticsShareModel> issuers,
                                      List<AnalyticsShareModel> sectors,
                                      List<AnalyticsShareModel> ratings,
                                      List<AnalyticsShareModel> currencies,
                                      List<AnalyticsShareModel> couponTypes,
                                      List<AnalyticsShareModel> maturities,
                                      WeightedValueModel yieldToMaturity,
                                      WeightedValueModel durationMacaulay,
                                      WeightedValueModel durationModified,
                                      AnalyticsShareModel largestIssuer,
                                      RateSensitivityModel rateSensitivity) {
}
