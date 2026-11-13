package ru.invest.api.portfolio.service.model;

import java.math.BigDecimal;

/**
 * Полный доход: что заплачено за покупки и что получено или стоит сейчас - с купонами, амортизацией, НКД, комиссиями и
 * налогом. Суммы - в валюте {@code currency}, на всё количество, деньги округлены до копеек.
 *
 * <pre>
 * доход от цены = продано и погашено + стоит сейчас + амортизация − куплено
 * НКД           = НКД при продажах + НКД сейчас − НКД при покупках
 * до налога     = доход от цены + купоны + НКД − комиссии
 * полный доход  = до налога − налог
 * </pre>
 *
 * @param currency           валюта сумм: валюта номинала, у дохода всего портфеля - rub
 * @param buyAmount          куплено: номинал на дату покупки × цена покупки × количество
 * @param buyAci             НКД, уплаченный при покупках
 * @param couponIncome       полученные купоны: размер купона × сколько облигаций было на дату выплаты
 * @param amortizationIncome полученные частичные погашения номинала
 * @param soldAmount         продано (номинал на дату продажи × цена) и погашено (остаток номинала)
 * @param saleAci            НКД, полученный при продажах
 * @param currentAmount      стоимость непроданных облигаций по текущей цене; null - цены нет
 * @param currentAci         НКД непроданных облигаций на сегодня
 * @param commission         комиссии брокера за покупки и продажи
 * @param priceIncome        доход от цены; null - нет текущей цены
 * @param aciIncome          НКД: полученный и текущий минус уплаченный
 * @param profit             доход до налога; null - нет текущей цены
 * @param taxRatePercent     ставка НДФЛ из запроса; null - налог не считается
 * @param tax                НДФЛ: ставка × положительный доход до налога - по каждой облигации отдельно, без сальдирования
 *                           между облигациями; 0 без ставки
 * @param fullIncome         полный доход; null - нет текущей цены
 * @param fullIncomePercent  полный доход в % к куплено + НКД при покупках
 * @param xirr               XIRR, % годовых, до налога; null - посчитать нельзя или итог неполный
 * @param unknownCouponCount сколько полученных купонов без размера в графике (не вошли в доход)
 * @param incomplete         итог неполный: купон без размера, НКД не посчитать, покупка раньше хранимой истории выплат,
 *                           облигация без цены или курса не вошла в итог - настоящий доход может быть больше или меньше
 */
public record IncomeModel(String currency,
                          BigDecimal buyAmount,
                          BigDecimal buyAci,
                          BigDecimal couponIncome,
                          BigDecimal amortizationIncome,
                          BigDecimal soldAmount,
                          BigDecimal saleAci,
                          BigDecimal currentAmount,
                          BigDecimal currentAci,
                          BigDecimal commission,
                          BigDecimal priceIncome,
                          BigDecimal aciIncome,
                          BigDecimal profit,
                          BigDecimal taxRatePercent,
                          BigDecimal tax,
                          BigDecimal fullIncome,
                          BigDecimal fullIncomePercent,
                          BigDecimal xirr,
                          int unknownCouponCount,
                          boolean incomplete) {
}
