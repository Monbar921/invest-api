package ru.invest.api.dto.bond.news;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ключевая ставка и курсы ЦБ. Поля ставки null, если ЦБ не ответил; валюты без курса в список не попадают.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class MarketOverviewDto {
    /** Ключевая ставка, % годовых */
    private BigDecimal keyRate;
    /** С какого дня действует ставка */
    private LocalDate keyRateSince;
    /** Ставка до последнего изменения */
    private BigDecimal previousKeyRate;
    /** Изменение ставки с начала года, п.п. */
    private BigDecimal keyRateYearChange;
    /** Дата последнего прошедшего заседания по ставке */
    private LocalDate keyRateMeetingDate;
    /** Изменение ставки по итогам последнего заседания, п.п.; 0 - ставку сохранили */
    private BigDecimal keyRateMeetingChange;
    private List<CurrencyRateDto> currencies;

    /**
     * Курс ЦБ: рублей за единицу валюты и изменение к прошлому курсу.
     */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class CurrencyRateDto {
        private String code;
        private BigDecimal rate;
        private BigDecimal change;
    }
}
