package ru.invest.api.dto.portfolio;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Продажа облигаций из портфеля, в том числе частичная. При изменении тикер не используется. Хватает ли облигаций в
 * покупках на дату продажи и не в будущем ли дата - проверяет invest-api (422).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioSaleRequest {
    /** Обязателен при создании */
    private String ticker;
    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be from 1 to 1000000")
    @Max(value = PortfolioPositionRequest.MAX_QUANTITY, message = "quantity must be from 1 to 1000000")
    private Integer quantity;
    /** Цена продажи, % от номинала; выкуп по оферте - 100 */
    @NotNull(message = "sellPrice is required")
    @DecimalMin(value = "0.01", message = "sellPrice must be from 0.01 to 300")
    @DecimalMax(value = "300", message = "sellPrice must be from 0.01 to 300")
    private BigDecimal sellPrice;
    /** Не задана - сегодня */
    private LocalDate sellDate;
    /** Полученный НКД на всю продажу, в валюте номинала; не задан - по графику купонов */
    @PositiveOrZero(message = "aci must not be negative")
    @DecimalMax(value = "1000000000000", message = "aci is too large")
    private BigDecimal aci;
    /** Комиссия брокера, в валюте номинала; не задана - 0 */
    @PositiveOrZero(message = "commission must not be negative")
    @DecimalMax(value = "1000000000000", message = "commission is too large")
    private BigDecimal commission;
    /** Имя брокера - как у покупки */
    @Size(max = PortfolioPositionRequest.MAX_BROKER_LENGTH, message = "broker must not be longer than 100 characters")
    private String broker;
}
