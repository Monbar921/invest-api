package ru.invest.api.dto.bond.calculation;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Доход за период владения облигацией. Денежные суммы - на всё количество, в валюте номинала (currency), до копеек.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ProfitCalculationDto {
    private String ticker;
    private String currency;
    private int quantity;
    private LocalDate buyDate;
    /** Дата выхода: продажа, оферта или погашение (если дата продажи позже погашения - погашение) */
    private LocalDate sellDate;
    private long days;
    private ExitTypeDto exitType;

    private BigDecimal buyPricePercent;
    /** При оферте и погашении - 100 */
    private BigDecimal sellPricePercent;

    /** Цена покупки без НКД */
    private BigDecimal purchaseAmount;
    /** НКД, доплаченный при покупке */
    private BigDecimal purchaseAci;
    private BigDecimal couponIncome;
    private int couponCount;
    private BigDecimal amortizationIncome;
    /** Продажа, выкуп по оферте или погашение остатка номинала, без НКД */
    private BigDecimal exitAmount;
    /** НКД, полученный при продаже */
    private BigDecimal exitAci;

    private BigDecimal profit;
    private BigDecimal profitPercent;
    /** Доход в % годовых без сложного процента */
    private BigDecimal annualProfitPercent;
    /** Ставка НДФЛ в %; null - налог не учитывался, тогда tax = 0, а netProfit = profit */
    private BigDecimal taxRatePercent;
    /** НДФЛ с положительного дохода */
    private BigDecimal tax;
    private BigDecimal netProfit;
    private BigDecimal netProfitPercent;

    /** Купоны за период с необъявленным размером (флоатер) - в доход не вошли */
    private int unknownCouponCount;
    /** НКД при покупке или продаже не посчитать - размер текущего купона не объявлен */
    private boolean aciUnknown;
    /** График амортизации не загружен - частичные погашения не учтены */
    private boolean amortizationScheduleMissing;
}
