package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * Закрытая позиция: облигации проданы или погашены целиком.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioClosedPositionDto {
    private String ticker;
    /** null - облигации уже нет в системе */
    private String bondName;
    private String isin;
    /** Валюта номинала; null - облигации нет */
    private String currency;
    /** Сколько было куплено */
    private int quantity;
    private LocalDate firstBuyDate;
    /** Последняя продажа или погашение */
    private LocalDate closeDate;
    /** null - облигации нет в системе */
    private PortfolioIncomeDto income;
}
