package ru.invest.api.dto.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;
import ru.invest.api.dto.bond.enums.RiskLevelDto;
import ru.invest.api.dto.bond.issuer.IssuerShortDto;

import java.time.ZonedDateTime;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BondDto {
    private String ticker;
    private String uid;
    private String isin;
    private String name;
    private String sector;
    private PriceDto price;
    private CouponDto coupon;
    private RiskLevelDto riskLevel;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime maturityDate;
    /** Дата ближайшей оферты; null - оферты нет */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime offerDate;
    /** Облигация с амортизацией: номинал гасится частями */
    private boolean amortizationFlag;
    /** Эмитент по данным Мосбиржи; заполняется только в карточке облигации, null - эмитент неизвестен */
    private IssuerShortDto issuer;
}
