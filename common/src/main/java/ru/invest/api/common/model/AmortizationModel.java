package ru.invest.api.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Частичное погашение номинала облигации.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class AmortizationModel {
    private String ticker;
    private String uid;
    /** Выплата на одну облигацию */
    private MoneyModel price;
    private LocalDateTime paymentDate;
    private AuditModel logged;
}
