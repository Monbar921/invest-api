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
    /** Сколько куплено */
    private int quantity;
    /** Сколько из покупки не продано (FIFO) и не погашено; суммы покупки - на это количество */
    private int remainingQuantity;
    private LocalDate buyDate;
    /** Имя брокера; null - не указан */
    private String broker;
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
    /** Сколько рублей заплачено, как ввёл пользователь; null - не вводил */
    private BigDecimal buyAmountRub;
    /** Стоимость покупки в рублях: введённая сумма, иначе по курсу ЦБ на дату покупки; null - курса нет */
    private BigDecimal buyAmountInRub;
    /** Текущая стоимость в рублях по курсу ЦБ на сегодня; null - цены или курса нет */
    private BigDecimal currentAmountInRub;
    /**
     * Доход в рублях: текущая стоимость в рублях − стоимость покупки в рублях, то есть вместе с изменением курса
     * валюты с даты покупки; null - цены или одного из курсов нет
     */
    private BigDecimal revenueInRub;
    /** Доходность к погашению, если купить сегодня по цене этой покупки (с сегодняшним НКД), % годовых */
    private BigDecimal yieldToMaturity;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    /** Уплаченный НКД на всю покупку, как ввёл пользователь; null - не вводил (считается по графику купонов) */
    private BigDecimal aci;
    /** Комиссия брокера за покупку, как ввёл пользователь; null - не вводил */
    private BigDecimal commission;
    private ZonedDateTime createdAt;
}
