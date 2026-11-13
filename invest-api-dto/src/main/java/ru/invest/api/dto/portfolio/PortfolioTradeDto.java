package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Сделка портфеля. Суммы - в валюте номинала, на всё количество.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioTradeDto {
    private PortfolioTradeTypeDto type;
    /** id покупки или продажи; у погашения null */
    private Long id;
    private String ticker;
    /** null - облигации уже нет в системе */
    private String bondName;
    /** null - облигации нет */
    private String currency;
    private LocalDate date;
    private int quantity;
    /** Цена, % от номинала; у погашения 100 */
    private BigDecimal price;
    /** Номинал на дату сделки × цена × количество; null - облигации нет */
    private BigDecimal amount;
    /** НКД сделки: введённый или по графику купонов; null - посчитать нельзя */
    private BigDecimal aci;
    /** НКД посчитан по графику купонов, а не введён */
    private boolean aciCalculated;
    /** Комиссия; null - не вводили */
    private BigDecimal commission;
    private String broker;
}
