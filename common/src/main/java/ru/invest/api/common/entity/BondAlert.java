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
import ru.invest.api.common.model.alert.AlertDirection;
import ru.invest.api.common.model.alert.AlertState;
import ru.invest.api.common.model.alert.AlertType;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    /** null - правило без облигации (ключевая ставка) */
    @Column(name = "ticker")
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private AlertType type;

    /** За сколько дней до события предупредить; только у правил по датам */
    @Column(name = "days")
    private Integer days;

    /** Направление порога; только у правил по цене и доходности */
    @Enumerated(EnumType.STRING)
    @Column(name = "direction")
    private AlertDirection direction;

    /** Порог: уровень цены в % от номинала, доходности в % годовых или изменение цены в % */
    @Column(name = "threshold")
    private BigDecimal threshold;

    /** Цена (% от номинала) при создании или включении правила - от неё считается PRICE_CHANGE */
    @Column(name = "base_value")
    private BigDecimal baseValue;

    /** Состояние порога уровня; null у остальных правил */
    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private AlertState state;

    /** Правила по новостям: id последней просмотренной новости - уведомляем только о более новых */
    @Column(name = "cursor_id")
    private Long cursorId;

    /** Объявленный купон: дата последнего купона с уже известным размером */
    @Column(name = "cursor_date")
    private LocalDate cursorDate;

    /** Сколько раз правило срабатывало: часть ключа против повторов у порогов */
    @Column(name = "trigger_count")
    private int triggerCount;

    @Column(name = "enabled")
    private boolean enabled;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Когда правило последний раз поставило уведомление в очередь, UTC */
    @Column(name = "last_triggered_at")
    private LocalDateTime lastTriggeredAt;
}
