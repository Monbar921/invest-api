package ru.invest.api.dto.portfolio.imports;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Сделка импорта */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class PortfolioImportItemDto {
    private Long id;
    /** Номер строки файла или операции */
    private int lineNo;
    /** null - строку не разобрать */
    private ImportKindDto kind;
    private LocalDate date;
    /** Тикер облигации в системе или из источника */
    private String ticker;
    private String isin;
    private String figi;
    private String name;
    private Integer quantity;
    /** Цена, % от номинала */
    private BigDecimal price;
    /** НКД на всю сделку, в валюте номинала; null - по графику купонов */
    private BigDecimal aci;
    /** Комиссия, в валюте номинала */
    private BigDecimal commission;
    /** Валюта номинала (rub, usd): в ней НКД и комиссия; null - облигация не найдена */
    private String currency;
    /** Рубли за покупку валютной облигации */
    private BigDecimal amountRub;
    private String broker;
    private ImportItemStatusDto status;
    /** Причина статуса или на что обратить внимание */
    private String message;
    /** Можно выбрать при подтверждении */
    private boolean selectable;
    /** id созданной покупки или продажи */
    private Long tradeId;
}
