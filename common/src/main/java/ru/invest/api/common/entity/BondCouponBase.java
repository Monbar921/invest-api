package ru.invest.api.common.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Купон выпуска по данным Мосбиржи (COUPON_DETAILS): «Ключевая ставка», «Ставка RUONIA», «ИПЦ», «Фиксированный с
 * известными купонами». Нужен флоатерам - к чему привязан купон. С облигацией связан по ISIN, без внешнего ключа.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "bond_coupon_base")
public class BondCouponBase {
    @Id
    @Column(name = "isin")
    private String isin;

    @Column(name = "coupon_details")
    private String couponDetails;

    @Embedded
    @AttributeOverride(name = "committedBy", column = @Column(name = "updated_by"))
    @AttributeOverride(name = "committedAt", column = @Column(name = "updated_at"))
    private Audit updated;
}
