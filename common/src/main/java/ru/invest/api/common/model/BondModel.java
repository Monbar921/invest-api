package ru.invest.api.common.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.enums.RiskLevel;
import ru.invest.api.common.model.rating.CreditRatingModel;
import ru.invest.api.common.model.screener.LiquidityModel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BondModel {
    private String ticker;
    private String uid;
    private String isin;
    private String name;
    private String sector;
    private String currency;
    private PriceModel price;
    private CouponModel coupon;
    private RiskLevel riskLevel;
    private LocalDateTime maturityDate;
    /** Дата ближайшей оферты; null - оферты нет */
    private LocalDateTime offerDate;
    private boolean amortizationFlag;
    /** Будущие частичные погашения номинала; заполняется только для расчёта доходности */
    private List<AmortizationModel> amortizations;
    /**
     * Кредитный рейтинг для сравнения: самый низкий из действующих рейтингов агентств (у каждого агентства - рейтинг
     * выпуска, а без него - эмитента); null - ни одного рейтинга АКРА или НКР нет.
     */
    private CreditRatingModel creditRating;
    /** Действующие рейтинги по агентствам - только в карточке облигации */
    private List<CreditRatingModel> creditRatings;
    /** Ликвидность по свечам за 30 дней; null - свечей нет */
    private LiquidityModel liquidity;
    /** Доходность кривой бескупонной доходности ОФЗ на срок дюрации облигации, % годовых; null - не посчитать */
    private BigDecimal ofzCurveYield;
    /** Спред к ОФЗ: доходность к погашению минус ofzCurveYield, п.п.; null - не посчитать */
    private BigDecimal spreadToOfz;
    /** Базовая ставка флоатера по данным Мосбиржи («Ключевая ставка», «Ставка RUONIA»); null - не флоатер или неизвестна */
    private String floaterBaseRate;
    /** Премия флоатера к базовой ставке, п.п.; бесплатного источника нет - всегда null */
    private BigDecimal floaterPremium;
    private AuditModel created;
    private AuditModel updated;
    private AuditModel logged;
}
