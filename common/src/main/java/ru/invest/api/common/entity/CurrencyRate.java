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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Курс валюты к рублю на дату. Уникален по (rate_date, currency).
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "currency_rate")
public class CurrencyRate {
    /** Откуда курс: ЦБ РФ */
    public static final String SOURCE_CB_RF = "CB_RF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rate_date")
    private LocalDate rateDate;

    /** Код валюты в верхнем регистре */
    @Column(name = "currency")
    private String currency;

    /** Рублей за одну единицу валюты */
    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "source")
    private String source;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
