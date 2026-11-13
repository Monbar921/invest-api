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
import ru.invest.api.common.model.account.SubscriptionSource;

import java.time.LocalDateTime;

/**
 * Подписка пользователя на тариф. Записи не удаляются: отзыв и замена новой выдачей ставят revokedAt.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "account_subscription")
public class AccountSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub Keycloak */
    @Column(name = "user_id")
    private String userId;

    /** id тарифа из конфигурации ru.invest.api.plans */
    @Column(name = "plan")
    private String plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private SubscriptionSource source;

    /** UTC */
    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    /** UTC, не включительно; null - бессрочно */
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    /** UTC; заполнено - отозвана */
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "comment")
    private String comment;

    /** Платёж, которым оплачена (source = PAYMENT) */
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;
}
