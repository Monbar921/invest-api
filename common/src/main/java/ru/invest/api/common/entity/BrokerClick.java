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

import java.time.LocalDate;

/**
 * Переходы к брокеру-партнёру по облигации за день. Пользователь не хранится.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "broker_click")
public class BrokerClick {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** День по Москве */
    @Column(name = "click_date")
    private LocalDate clickDate;

    @Column(name = "partner_id")
    private String partnerId;

    @Column(name = "ticker")
    private String ticker;

    @Column(name = "clicks")
    private Long clicks;
}
