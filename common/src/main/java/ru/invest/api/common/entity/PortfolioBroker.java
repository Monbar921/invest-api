package ru.invest.api.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Брокер пользователя - метка покупки в портфеле. Имя уникально у пользователя без учёта регистра.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "portfolio_broker")
public class PortfolioBroker {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub Keycloak */
    @Column(name = "user_id")
    private String userId;

    @Column(name = "name")
    private String name;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
