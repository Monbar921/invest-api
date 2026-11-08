package ru.invest.api.common.model.news;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Ключевая ставка и курсы валют ЦБ для шапки ленты новостей. Любая часть может быть null, если источник не ответил.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class MarketOverviewModel {
    /** Действующая ключевая ставка, % годовых */
    private BigDecimal keyRate;
    /** С какого дня действует ставка */
    private LocalDate keyRateSince;
    /** Ставка до последнего изменения */
    private BigDecimal previousKeyRate;
    /** Изменение ставки с начала года, п.п.; null - нет ставки на 1 января */
    private BigDecimal keyRateYearChange;
    /** Последнее прошедшее заседание Совета директоров по ставке; null - календарь заседаний не задан */
    private LocalDate keyRateMeetingDate;
    /** Изменение ставки по итогам последнего заседания, п.п.; 0 - ставку сохранили */
    private BigDecimal keyRateMeetingChange;
    private List<CurrencyRateModel> currencies = List.of();

    /**
     * Курс ЦБ: сколько рублей за единицу валюты.
     */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class CurrencyRateModel {
        private String code;
        private BigDecimal rate;
        /** Изменение к предыдущему установленному курсу, рубли */
        private BigDecimal change;
    }
}
