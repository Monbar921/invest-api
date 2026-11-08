package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Позиция портфеля с текущей оценкой. Суммы - в валюте номинала, на всё количество.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioPositionDto {
    private Long id;
    private String ticker;
    /** null - облигации уже нет в системе */
    private String bondName;
    private String isin;
    /** Валюта номинала */
    private String currency;
    private int quantity;
    private LocalDate buyDate;
    /** Текущий номинал одной облигации */
    private BigDecimal nominal;
    /** Цена покупки, % от номинала */
    private BigDecimal buyPrice;
    /** Текущая цена, % от номинала; null - цены нет */
    private BigDecimal currentPrice;
    /** Стоимость покупки: номинал × цена покупки × количество */
    private BigDecimal buyAmount;
    /** Текущая стоимость; null - цены нет */
    private BigDecimal currentAmount;
    /** Текущая стоимость − стоимость покупки; отрицательный - убыток; null - цены нет */
    private BigDecimal revenue;
    /** Доход в % к стоимости покупки; null - цены нет */
    private BigDecimal revenuePercent;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime createdAt;
}
