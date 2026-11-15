package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * Структура и риск открытых позиций портфеля. Доли - от стоимости оценённых позиций в рублях; группа «нет данных»
 * (key = null) - в конце каждого разреза.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioAnalyticsDto {
    /** Стоимость оценённых позиций в рублях; null - оценённых нет */
    private BigDecimal valueInRub;
    /** Облигаций в открытых позициях, с неоценёнными */
    private int bonds;
    private List<PortfolioUnvaluedPositionDto> unvalued;
    /** По убыванию доли */
    private List<PortfolioAnalyticsShareDto> issuers;
    private List<PortfolioAnalyticsShareDto> sectors;
    /** От лучшей ступени к худшей */
    private List<PortfolioAnalyticsShareDto> ratings;
    private List<PortfolioAnalyticsShareDto> currencies;
    private List<PortfolioAnalyticsShareDto> couponTypes;
    /** Лестница погашений: год даты погашения по возрастанию */
    private List<PortfolioAnalyticsShareDto> maturities;
    /** % годовых */
    private PortfolioAnalyticsWeightedDto yieldToMaturity;
    /** Лет */
    private PortfolioAnalyticsWeightedDto durationMacaulay;
    /** Лет */
    private PortfolioAnalyticsWeightedDto durationModified;
    /** Крупнейший эмитент; null - эмитент не известен ни у одной позиции */
    private PortfolioAnalyticsShareDto largestIssuer;
    /** null - нет облигаций с постоянным купоном и дюрацией */
    private PortfolioRateSensitivityDto rateSensitivity;
}
