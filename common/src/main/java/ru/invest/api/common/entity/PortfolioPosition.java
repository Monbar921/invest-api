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
 * Позиция портфеля пользователя - одна покупка облигации. Облигация - по тикеру, без внешнего ключа.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "portfolio_position")
public class PortfolioPosition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** sub Keycloak */
    @Column(name = "user_id")
    private String userId;

    @Column(name = "ticker")
    private String ticker;

    /** Количество облигаций */
    @Column(name = "quantity")
    private int quantity;

    /** Цена покупки, % от номинала */
    @Column(name = "buy_price")
    private BigDecimal buyPrice;

    @Column(name = "buy_date")
    private LocalDate buyDate;

    /**
     * Сколько рублей заплачено за покупку валютной облигации (без НКД и комиссии); null - рублёвая стоимость покупки
     * считается по курсу ЦБ на дату покупки
     */
    @Column(name = "buy_amount_rub")
    private BigDecimal buyAmountRub;

    /** Уплаченный НКД на всю покупку, в валюте номинала; null - не вводили, считается по графику купонов */
    @Column(name = "aci")
    private BigDecimal aci;

    /** Комиссия брокера за покупку, в валюте номинала; null - не вводили (0) */
    @Column(name = "commission")
    private BigDecimal commission;

    /**
     * Ключ сделки у источника импорта (операция T-Invest, строка отчёта брокера); уникален у пользователя - повторный
     * импорт той же сделки её пропускает. null - покупки введена вручную
     */
    @Column(name = "import_key")
    private String importKey;

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
