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
 * Продажа облигаций из портфеля, в том числе частичная. Какие покупки она закрывает, считается по FIFO при чтении
 * портфеля - ссылки на покупки нет. Облигация - по тикеру, без внешнего ключа.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "portfolio_sale")
public class PortfolioSale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub Keycloak */
    @Column(name = "user_id")
    private String userId;

    @Column(name = "ticker")
    private String ticker;

    /** Количество проданных облигаций */
    @Column(name = "quantity")
    private int quantity;

    /** Цена продажи, % от номинала */
    @Column(name = "sell_price")
    private BigDecimal sellPrice;

    @Column(name = "sell_date")
    private LocalDate sellDate;

    /** Полученный НКД на всю продажу, в валюте номинала; null - не вводили, считается по графику купонов */
    @Column(name = "aci")
    private BigDecimal aci;

    /** Комиссия брокера за продажу, в валюте номинала; null - не вводили (0) */
    @Column(name = "commission")
    private BigDecimal commission;

    /** Брокер ({@link PortfolioBroker}); null - не указан */
    @Column(name = "broker_id")
    private Long brokerId;

    /** UTC */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** UTC */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
