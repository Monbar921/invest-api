package ru.invest.api.dto.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CouponDto {
    private String uid;
    private int quantityPerYear;
    private BigDecimal interest;
    /** true - постоянный (фиксированный) купон, false - переменный (флоатер) */
    private Boolean isFixed;
    /** Эффективная доходность к погашению в % годовых, с учётом НКД и амортизации; null - посчитать нельзя */
    private BigDecimal yieldToMaturity;
    /** Эффективная доходность к оферте в % годовых (выкуп по номиналу); null - оферты нет или посчитать нельзя */
    private BigDecimal yieldToOffer;
    /** Дюрация Маколея к погашению, лет; null - доходность к погашению не посчитать */
    private BigDecimal durationMacaulay;
    /** Модифицированная дюрация, лет: изменение цены в % при сдвиге доходности на 1 п.п. */
    private BigDecimal durationModified;
    private List<CouponDataDto> couponData;
}
