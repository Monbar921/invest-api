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

/**
 * Сделка из источника импорта: покупка или продажа облигации, её статус в предпросмотре и итог после подтверждения.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Accessors(chain = true)
@Table(name = "portfolio_import_item")
public class PortfolioImportItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@link PortfolioImport} */
    @Column(name = "import_id")
    private Long importId;

    /** Порядок в источнике: номер строки файла или операции */
    @Column(name = "line_no")
    private int lineNo;

    /** BUY, SELL */
    @Column(name = "kind")
    private String kind;

    /** Ключ сделки у источника - тот же ставится созданной покупке или продаже ({@code import_key}) */
    @Column(name = "import_key")
    private String importKey;

    @Column(name = "trade_date")
    private LocalDate tradeDate;

    /** Тикер облигации в системе; null - не нашлась */
    @Column(name = "ticker")
    private String ticker;

    @Column(name = "isin")
    private String isin;

    @Column(name = "figi")
    private String figi;

    /** Название из системы, а если облигации нет - из источника */
    @Column(name = "name")
    private String name;

    @Column(name = "quantity")
    private Integer quantity;

    /** Цена, % от номинала */
    @Column(name = "price")
    private BigDecimal price;

    /** НКД на всю сделку, в валюте номинала; null - источник не дал */
    @Column(name = "aci")
    private BigDecimal aci;

    /** Комиссия, в валюте номинала; null - источник не дал */
    @Column(name = "commission")
    private BigDecimal commission;

    /** Рубли за покупку валютной облигации, если брокер списал рубли */
    @Column(name = "amount_rub")
    private BigDecimal amountRub;

    /** Имя брокера для метки сделки */
    @Column(name = "broker")
    private String broker;

    /**
     * NEW, POSSIBLE_DUPLICATE, NEEDS_REVIEW, DUPLICATE, UNKNOWN_BOND, INVALID; после подтверждения - CREATED, FAILED,
     * SKIPPED
     */
    @Column(name = "status")
    private String status;

    /** Почему не распознана, не создана или на что обратить внимание */
    @Column(name = "message")
    private String message;

    /** id созданной покупки или продажи */
    @Column(name = "trade_id")
    private Long tradeId;
}
