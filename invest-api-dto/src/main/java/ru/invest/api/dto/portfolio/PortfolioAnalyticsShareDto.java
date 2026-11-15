package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * Доля группы позиций в оценённой части портфеля.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioAnalyticsShareDto {
    /**
     * Значение признака: id эмитента Мосбиржи, сектор T-Invest (в нижнем регистре), ступень рейтинга («AA-»), валюта
     * номинала (в нижнем регистре), FIXED / FLOATING, год погашения; null - «нет данных»
     */
    private String key;
    /** Для показа: у эмитента - короткое название, у рейтинга - ступень; иначе null */
    private String title;
    private BigDecimal valueInRub;
    /** Доля, %, 2 знака; доли одного разреза в сумме - ровно 100 */
    private BigDecimal share;
    /** Облигации группы по убыванию стоимости */
    private List<String> tickers;
}
