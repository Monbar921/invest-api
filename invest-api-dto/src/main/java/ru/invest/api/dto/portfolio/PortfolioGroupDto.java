package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Все покупки одной облигации одной строкой: суммы - сложение сумм покупок, в валюте номинала.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioGroupDto {
    private String ticker;
    /** null - облигации уже нет в системе */
    private String bondName;
    private String isin;
    /** Валюта номинала */
    private String currency;
    /** Сколько облигаций во всех покупках */
    private int quantity;
    /** Разные брокеры покупок по имени; пусто - ни у одной покупки брокер не указан */
    private List<String> brokers;
    private LocalDate firstBuyDate;
    private LocalDate lastBuyDate;
    /** Текущий номинал одной облигации */
    private BigDecimal nominal;
    /** Средняя цена покупки, % от номинала, взвешенная по количеству */
    private BigDecimal buyPrice;
    /** Текущая цена, % от номинала; null - цены нет */
    private BigDecimal currentPrice;
    private BigDecimal buyAmount;
    /** null - цены нет */
    private BigDecimal currentAmount;
    /** null - цены нет */
    private BigDecimal revenue;
    /** Доход в % к стоимости покупки; null - цены нет */
    private BigDecimal revenuePercent;
    /** null - хотя бы у одной покупки нет рублёвой суммы */
    private BigDecimal buyAmountInRub;
    /** null - цены или курса нет */
    private BigDecimal currentAmountInRub;
    /** null - нет одной из рублёвых сумм */
    private BigDecimal revenueInRub;
    /** Покупки по дате */
    private List<PortfolioPositionDto> positions;
}
