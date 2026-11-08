package ru.invest.api.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.alert.AlertType;

import java.time.LocalDateTime;

/**
 * Правило уведомления пользователя по облигации. Облигация - по тикеру, без внешнего ключа.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "bond_alert")
public class BondAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub Keycloak */
    @Column(name = "user_id")
    private String userId;

    @Column(name = "ticker")
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private AlertType type;

    /** За сколько дней до события предупредить */
    @Column(name = "days")
    private Integer days;

    @Column(name = "enabled")
    private boolean enabled;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Когда правило последний раз поставило уведомление в очередь, UTC */
    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;
}
