package ru.invest.api.dto.portfolio;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
    /** Длина имени брокера - как в БД */
    public static final int MAX_BROKER_LENGTH = 100;

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
    /**
     * Имя брокера: уже есть у пользователя (без учёта регистра) - он, нет - создаётся и доступен для следующих
     * покупок. Не задано или пусто - без брокера.
     */
    @Size(max = MAX_BROKER_LENGTH, message = "broker must not be longer than 100 characters")
    private String broker;
    /**
     * Сколько рублей заплачено за покупку валютной облигации, без НКД и комиссии (из отчёта брокера). Не задано -
     * рублёвая стоимость покупки по курсу ЦБ на дату покупки. У рублёвой облигации - 422.
     */
    @Positive(message = "buyAmountRub must be positive")
    @DecimalMax(value = "1000000000000", message = "buyAmountRub is too large")
    private BigDecimal buyAmountRub;
    /** Уплаченный НКД на всю покупку, в валюте номинала (из отчёта брокера); не задан - по графику купонов */
    @PositiveOrZero(message = "aci must not be negative")
    @DecimalMax(value = "1000000000000", message = "aci is too large")
    private BigDecimal aci;
    /** Комиссия брокера за покупку, в валюте номинала; не задана - 0 */
    @PositiveOrZero(message = "commission must not be negative")
    @DecimalMax(value = "1000000000000", message = "commission is too large")
    private BigDecimal commission;
}
