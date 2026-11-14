package ru.invest.api.dto.portfolio.imports;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/** Бумага из источника, которой нет среди облигаций системы */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class ImportUnknownBondDto {
    private String isin;
    private String figi;
    private String ticker;
    private String name;
    /** Сколько сделок с ней */
    private int trades;
}
