package ru.invest.api.dto.bond.calculation;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Параметры расчёта дохода за период владения облигацией. Все поля необязательны.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ProfitCalculationRequest {
    /** Дата покупки, не раньше сегодняшней; null - сегодня */
    private LocalDate buyDate;
    /** Дата продажи; null или не раньше погашения - держим до погашения, в дату оферты - выкуп по номиналу */
    private LocalDate sellDate;
    /** Количество облигаций; null - одна */
    private Integer quantity;
    /** Цена покупки в % от номинала; null - текущая */
    private BigDecimal buyPricePercent;
    /** Цена продажи в % от номинала для продажи раньше погашения и не в оферту; null - текущая */
    private BigDecimal sellPricePercent;
    /** Ставка НДФЛ в % (13 или 15); null - налог не учитывать */
    private BigDecimal taxRatePercent;
}
