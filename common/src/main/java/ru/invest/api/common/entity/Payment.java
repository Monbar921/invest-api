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
import ru.invest.api.common.model.payment.PaymentKind;
import ru.invest.api.common.model.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Платёж за тариф. Записи не удаляются - это история платежей пользователя.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "payment")
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub Keycloak */
    @Column(name = "user_id")
    private String userId;

    /** id тарифа из конфигурации ru.invest.api.plans */
    @Column(name = "plan")
    private String plan;

    /** Покупаемый срок, ISO-8601: P1M */
    @Column(name = "period")
    private String period;

    /** Рубли */
    @Column(name = "amount")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    private PaymentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    /** id провайдера: tbank */
    @Column(name = "provider")
    private String provider;

    /** Номер заказа у провайдера (OrderId Т-Кассы): наш, уникальный */
    @Column(name = "order_id")
    private String orderId;

    /** id платежа у провайдера; null - провайдер ещё не ответил */
    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    /** Статус провайдера как есть - для разбора */
    @Column(name = "provider_status")
    private String providerStatus;

    /** Страница оплаты провайдера; у автопродления нет */
    @Column(name = "confirmation_url")
    private String confirmationUrl;

    /** Маска карты от провайдера: 430000******0777 */
    @Column(name = "card_pan")
    private String cardPan;

    /** Почему не прошёл: сообщение провайдера */
    @Column(name = "error_message")
    private String errorMessage;

    /** Автопродление, которое создало платёж (RENEWAL) */
    @Column(name = "auto_renewal_id")
    private Long autoRenewalId;

    /** Подписка, выданная этим платежом; заполнена - платёж применён, повтор ничего не меняет */
    @Column(name = "subscription_id")
    private Long subscriptionId;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** UTC: когда стал SUCCEEDED */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** UTC */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
