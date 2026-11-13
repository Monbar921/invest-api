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
 * Автопродление тарифа по сохранённой карте. У пользователя не больше одного действующего (cancelled_at is null);
 * отключённые остаются для истории.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "account_auto_renewal")
public class AccountAutoRenewal {
    /** Отключил пользователь */
    public static final String CANCEL_USER = "USER";
    /** Тариф выдан или отозван вручную */
    public static final String CANCEL_MANUAL = "MANUAL";
    /** Куплен другой тариф или новая карта */
    public static final String CANCEL_REPLACED = "REPLACED";
    /** Списания не прошли max-attempts раз подряд */
    public static final String CANCEL_PAYMENT_FAILED = "PAYMENT_FAILED";
    /** У тарифа больше нет цены или его убрали из конфигурации */
    public static final String CANCEL_PLAN_NOT_SOLD = "PLAN_NOT_SOLD";
    /** Платёж, которым включено, возвращён */
    public static final String CANCEL_REFUNDED = "REFUNDED";
    /** Срок списания давно прошёл (шедулер не работал): списывать задним числом не будем */
    public static final String CANCEL_EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub Keycloak */
    @Column(name = "user_id")
    private String userId;

    /** Какой тариф продлевать */
    @Column(name = "plan")
    private String plan;

    @Column(name = "provider")
    private String provider;

    /** Токен сохранённой карты у провайдера (RebillId Т-Кассы); это не номер карты */
    @Column(name = "rebill_id")
    private String rebillId;

    /** Маска карты: 430000******0777 */
    @Column(name = "card_pan")
    private String cardPan;

    /** UTC: когда списать в следующий раз */
    @Column(name = "next_charge_at")
    private LocalDateTime nextChargeAt;

    /** Неудачных списаний подряд */
    @Column(name = "failed_attempts")
    private int failedAttempts;

    /** Платёж, которым включено */
    @Column(name = "payment_id")
    private Long paymentId;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** UTC; заполнено - отключено */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** Почему отключено: константы CANCEL_* */
    @Column(name = "cancel_reason")
    private String cancelReason;
}
