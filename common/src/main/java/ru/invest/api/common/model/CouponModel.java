package ru.invest.api.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CouponModel {
    private String uid;
    private BigDecimal interest;
    /** Эффективная доходность к погашению в % годовых, с учётом НКД и амортизации; null - посчитать нельзя */
    private BigDecimal yieldToMaturity;
    /** Эффективная доходность к оферте в % годовых, с учётом НКД; null - оферты нет или посчитать нельзя */
    private BigDecimal yieldToOffer;
    /** Дюрация Маколея к погашению, лет - считается вместе с доходностью к погашению; null - доходности нет */
    private BigDecimal durationMacaulay;
    /** Модифицированная дюрация к погашению, лет: на сколько % изменится цена при сдвиге доходности на 1 п.п. */
    private BigDecimal durationModified;
    private Boolean isFixed;
    private int quantityPerYear;
    private List<CouponDataModel> couponData;
}
