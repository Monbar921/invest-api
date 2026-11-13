package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Полный доход: доход от цены + купоны + НКД − комиссии − налог. Суммы - в {@code currency}, на всё количество.
 * <pre>
 * доход от цены = продано и погашено + стоит сейчас + амортизация − куплено
 * НКД           = НКД при продажах + НКД сейчас − НКД при покупках
 * до налога     = доход от цены + купоны + НКД − комиссии
 * полный доход  = до налога − налог
 * </pre>
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioIncomeDto {
    /** Валюта номинала; у дохода всего портфеля - rub */
    private String currency;
    /** Куплено: номинал на дату покупки × цена покупки × количество */
    private BigDecimal buyAmount;
    /** НКД, уплаченный при покупках */
    private BigDecimal buyAci;
    /** Полученные купоны: размер × сколько облигаций было на дату выплаты */
    private BigDecimal couponIncome;
    /** Полученные частичные погашения номинала */
    private BigDecimal amortizationIncome;
    /** Продано и погашено */
    private BigDecimal soldAmount;
    /** НКД, полученный при продажах */
    private BigDecimal saleAci;
    /** Стоимость непроданного по текущей цене; null - цены нет */
    private BigDecimal currentAmount;
    /** НКД непроданного на сегодня */
    private BigDecimal currentAci;
    /** Комиссии за покупки и продажи */
    private BigDecimal commission;
    /** Доход от цены (с возвращённым амортизацией номиналом); null - цены нет */
    private BigDecimal priceIncome;
    /** НКД: полученный и текущий минус уплаченный */
    private BigDecimal aciIncome;
    /** До налога; null - цены нет */
    private BigDecimal profit;
    /** Ставка НДФЛ из запроса; null - налог не считался */
    private BigDecimal taxRatePercent;
    /** НДФЛ по облигациям отдельно, без сальдирования; null - цены нет */
    private BigDecimal tax;
    /** Полный доход после налога; null - цены нет */
    private BigDecimal fullIncome;
    /** Полный доход в % к куплено + НКД при покупках */
    private BigDecimal fullIncomePercent;
    /** XIRR, % годовых, до налога; null - посчитать нельзя или итог неполный */
    private BigDecimal xirr;
    /** Полученные купоны без размера в графике - в доход не вошли */
    private int unknownCouponCount;
    /** Итог неполный: настоящий доход может отличаться */
    private boolean incomplete;
}
