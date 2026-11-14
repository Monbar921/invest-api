package ru.invest.api.dto.bond.screener;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.dto.bond.MoneyDto;

import java.math.BigDecimal;

/**
 * Ликвидность по дневным свечам: средние за торговый день биржи в последние periodDays дней.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class LiquidityDto {
    /** Средний дневной объём, штук */
    private BigDecimal avgDailyVolume;
    /** Средний дневной оборот в валюте номинала */
    private MoneyDto avgDailyTurnover;
    /** Средний дневной оборот в рублях; null - нет курса */
    private BigDecimal avgDailyTurnoverRub;
    private int tradingDays;
    private int periodDays;
}
