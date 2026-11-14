package ru.invest.api.dto.bond;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;
import ru.invest.api.dto.bond.enums.RiskLevelDto;
import ru.invest.api.dto.bond.issuer.IssuerShortDto;
import ru.invest.api.dto.bond.screener.CreditRatingDto;
import ru.invest.api.dto.bond.screener.LiquidityDto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

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
    /** Рейтинг для сравнения: самый низкий из действующих рейтингов АКРА и НКР; null - рейтинга нет */
    private CreditRatingDto creditRating;
    /** Рейтинги по агентствам - только в карточке облигации */
    private List<CreditRatingDto> creditRatings;
    /** Ликвидность по свечам за 30 дней; null - свечей нет */
    private LiquidityDto liquidity;
    /** Доходность кривой ОФЗ на срок дюрации облигации, % годовых */
    private BigDecimal ofzCurveYield;
    /** Спред к ОФЗ: доходность к погашению минус ofzCurveYield, п.п.; только рублёвые облигации */
    private BigDecimal spreadToOfz;
    /** База ставки флоатера по данным Мосбиржи («Ключевая ставка», «Ставка RUONIA»); null - не флоатер или неизвестна */
    private String floaterBaseRate;
    /** Премия флоатера к базе, п.п.; источника нет - всегда null */
    private BigDecimal floaterPremium;
}
