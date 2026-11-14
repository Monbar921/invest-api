package ru.invest.api.common.model.screener;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.MoneyModel;

import java.math.BigDecimal;

/**
 * Ликвидность облигации по дневным свечам за последние 30 дней.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class LiquidityModel {
    /** Средний дневной объём, штук (лотов T-Invest) */
    private BigDecimal avgDailyVolume;
    /** Средний дневной оборот в валюте номинала */
    private MoneyModel avgDailyTurnover;
    /** Средний дневной оборот в рублях по курсу на сегодня; null - нет курса */
    private BigDecimal avgDailyTurnoverRub;
    /** Торговых дней биржи в окне - на них делится сумма */
    private int tradingDays;
    /** Дней в окне */
    private int periodDays;
}
