package ru.invest.api.common.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "bond_id", referencedColumnName = "id")
    private Bond bond;

    @Column(name = "ticker")
    private Integer ticker;

    @Column(name = "quantity_per_year")
    private Integer quantityPerYear;

    @Column(name = "quantity_per_year")
    private Boolean isFixedCoupon;

    @Column(name = "nominal_interest")
    private BigDecimal nominalInterest;

    @Column(name = "current_interest")
    private BigDecimal currentInterest;

    @OneToMany(mappedBy = "coupon")
    private List<CouponData> couponData;

    @Embedded
    @AttributeOverride(name = "committedBy", column = @Column(name = "created_by"))
    @AttributeOverride(name = "committedAt", column = @Column(name = "created_at"))
    private Audit created;

    @Embedded
    @AttributeOverride(name = "committedBy", column = @Column(name = "updated_by"))
    @AttributeOverride(name = "committedAt", column = @Column(name = "updated_at"))
    private Audit updated;
}
