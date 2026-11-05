package ru.invest.api.common.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Эмитент выпуска по данным Мосбиржи. С облигацией связан по ISIN.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "bond_issuer")
public class BondIssuer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "isin")
    private String isin;

    @Column(name = "emitter_id")
    private Long emitterId;

    @Column(name = "title")
    private String title;

    @Column(name = "inn")
    private String inn;

    @Embedded
    @AttributeOverride(name = "committedBy", column = @Column(name = "updated_by"))
    @AttributeOverride(name = "committedAt", column = @Column(name = "updated_at"))
    private Audit updated;
}
