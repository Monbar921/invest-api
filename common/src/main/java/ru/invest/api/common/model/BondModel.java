package ru.invest.api.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.enums.RiskLevel;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BondModel {
    private String ticker;
    private String uid;
    private String isin;
    private String name;
    private String sector;
    private String currency;
    private PriceModel price;
    private CouponModel coupon;
    private RiskLevel riskLevel;
    private LocalDateTime maturityDate;
    /** Дата ближайшей оферты; null - оферты нет */
    private LocalDateTime offerDate;
    private boolean amortizationFlag;
    /** Будущие частичные погашения номинала; заполняется только для расчёта доходности */
    private List<AmortizationModel> amortizations;
    private AuditModel created;
    private AuditModel updated;
    private AuditModel logged;
}
