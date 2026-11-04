package ru.invest.api.dto.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;
import ru.invest.api.dto.bond.enums.RiskLevelDto;

import java.time.ZonedDateTime;

/**
 * Облигация в результатах поиска - только общие сведения, без цены и купонов.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BondSearchItemDto {
    private String ticker;
    private String uid;
    private String isin;
    private String name;
    private String sector;
    private String currency;
    private RiskLevelDto riskLevel;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime maturityDate;
}
