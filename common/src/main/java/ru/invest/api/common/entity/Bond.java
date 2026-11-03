package ru.invest.api.common.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.enums.RiskLevel;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "bond")
@Accessors(chain = true)
public class Bond {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(mappedBy = "bond", cascade = CascadeType.ALL, orphanRemoval = true)
    private Coupon coupon;

    @OneToOne(mappedBy = "bond", cascade = CascadeType.ALL, orphanRemoval = true)
    private Price price;

    /** График частичных погашений номинала - заполняется только для облигаций с амортизацией */
    @OneToMany(mappedBy = "bond", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("paymentDate asc")
    private List<Amortization> amortizations;

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

    @Column(name = "currency")
    private String currency;

    @Column(name = "maturity_date")
    private LocalDateTime maturityDate;

    @Column(name = "offer_date")
    private LocalDateTime offerDate;

    @Column(name = "amortization_flag")
    private boolean amortizationFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Embedded
    @AttributeOverride(name = "committedBy", column = @Column(name = "created_by"))
    @AttributeOverride(name = "committedAt", column = @Column(name = "created_at"))
    private Audit created;

    @Embedded
    @AttributeOverride(name = "committedBy", column = @Column(name = "updated_by"))
    @AttributeOverride(name = "committedAt", column = @Column(name = "updated_at"))
    private Audit updated;
}
