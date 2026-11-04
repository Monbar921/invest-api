package ru.invest.api.common.model.candle;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Свеча цены облигации в % от номинала. date - день свечи, для недели и месяца - первый день периода.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CandleModel {
    private LocalDate date;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    /** Объём торгов в лотах */
    private long volume;
}
