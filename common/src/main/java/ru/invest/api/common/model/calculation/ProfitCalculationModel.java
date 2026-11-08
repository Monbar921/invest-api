package ru.invest.api.common.model.calculation;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Доход по облигации за период владения. Денежные суммы - на всё количество, в валюте номинала облигации.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ProfitCalculationModel {
    private String ticker;
    private String currency;
    private int quantity;
    private LocalDate buyDate;
    /** Дата выхода из позиции: дата продажи, оферты или погашения */
    private LocalDate sellDate;
    private long days;
    private ExitType exitType;

    private BigDecimal buyPricePercent;
    /** Цена продажи в % от номинала; при оферте и погашении - 100 */
    private BigDecimal sellPricePercent;

    /** Сколько заплатили за облигации без НКД */
    private BigDecimal purchaseAmount;
    /** НКД, доплаченный продавцу при покупке */
    private BigDecimal purchaseAci;
    /** Купоны с выплатой после покупки и до выхода включительно */
    private BigDecimal couponIncome;
    private int couponCount;
    /** Частичные погашения номинала за период */
    private BigDecimal amortizationIncome;
    /** Продажа, выкуп по оферте или погашение остатка номинала, без НКД */
    private BigDecimal exitAmount;
    /** НКД, полученный при продаже */
    private BigDecimal exitAci;

    private BigDecimal profit;
    private BigDecimal profitPercent;
    /** Доход в % годовых без сложного процента: profitPercent x 365 / days */
    private BigDecimal annualProfitPercent;
    /** Ставка НДФЛ в %; null - налог не учитывался, тогда tax = 0, а netProfit = profit */
    private BigDecimal taxRatePercent;
    /** НДФЛ с положительного дохода */
    private BigDecimal tax;
    private BigDecimal netProfit;
    private BigDecimal netProfitPercent;

    /**
     * Вклад на ту же сумму (покупка с НКД) и тот же срок: простые проценты без капитализации, НДФЛ - по той же ставке.
     * Все поля null, если ставка вклада не задана.
     */
    private BigDecimal depositRatePercent;
    private BigDecimal depositProfit;
    private BigDecimal depositTax;
    private BigDecimal depositNetProfit;

    /** Купоны за период, размер которых ещё не объявлен (флоатер) - в доход не вошли */
    private int unknownCouponCount;
    /** НКД при покупке или продаже не посчитать: размер текущего купона не объявлен - принят нулевым */
    private boolean aciUnknown;
    /** У облигации амортизация, но график не загружен - частичные погашения не учтены */
    private boolean amortizationScheduleMissing;
}
