package ru.invest.api.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PriceModel {
    private String uid;
    private String ticker;
    private MoneyModel nominal;
    private MoneyModel current;
    /**
     * Текущая цена в рублях по курсу ЦБ - для фильтрации и сортировки облигаций в разных валютах.
     * Заполняется при загрузке облигаций в кэш, наружу не отдаётся; null - цены нет или нет курса.
     */
    private BigDecimal currentInRub;
    private BigDecimal percentagePrice;
    private AuditModel created;
    private AuditModel updated;
    private AuditModel logged;
}
