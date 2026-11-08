package ru.invest.api.dto.portfolio;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Покупка облигации в портфель. При изменении позиции тикер не используется. Дату в будущем проверяет invest-api
 * (422): «сегодня» считается в его часовом поясе.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioPositionRequest {
    /** Самое большое количество облигаций в одной покупке; на бэке то же ограничение */
    public static final int MAX_QUANTITY = 1_000_000;

    /** Обязателен при создании */
    private String ticker;
    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be from 1 to 1000000")
    @Max(value = MAX_QUANTITY, message = "quantity must be from 1 to 1000000")
    private Integer quantity;
    /** Цена покупки, % от номинала */
    @NotNull(message = "buyPrice is required")
    @DecimalMin(value = "0.01", message = "buyPrice must be from 0.01 to 300")
    @DecimalMax(value = "300", message = "buyPrice must be from 0.01 to 300")
    private BigDecimal buyPrice;
    /** Не задана - сегодня */
    private LocalDate buyDate;
}
