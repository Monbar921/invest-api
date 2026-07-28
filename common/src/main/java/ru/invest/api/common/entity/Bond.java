package ru.invest.api.common.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.invest.api.common.model.enums.RiskLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "bond")
public class Bond {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticker")
    private String ticker;

    @Column(name = "uid")
    private String uid;

    @Column(name = "isin")
    private String isin;

    @Column(name = "name")
    private String name;

    @Column(name = "sector")
    private String sector;

    @Column(name = "nominal_price")
    private BigDecimal nominalPrice;

    @Column(name = "nominal_currency")
    private String nominalCurrency;

    @Column(name = "is_fixed_coupon")
    private Boolean isFixedCoupon;

    @Column(name = "currency")
    private String currency;

    @Column(name = "maturity_date")
    private LocalDateTime maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Column(name = "coupon_quantity_per_year")
    private Integer couponQuantityPerYear;

    @OneToMany(mappedBy = "bond")
    private List<Coupon> coupons;

    @Embedded
    @AttributeOverride(name = "committedBy", column = @Column(name = "created_by"))
    @AttributeOverride(name = "committedAt", column = @Column(name = "created_at"))
    private Audit created;

    @Embedded
    @AttributeOverride(name = "committedBy", column = @Column(name = "updated_by"))
    @AttributeOverride(name = "committedAt", column = @Column(name = "updated_at"))
    private Audit updated;
}
