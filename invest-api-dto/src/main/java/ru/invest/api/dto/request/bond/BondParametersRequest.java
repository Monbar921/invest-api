package ru.invest.api.dto.request.bond;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.dto.bond.enums.RiskLevelDto;

import java.util.List;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class BondParametersRequest {
    private List<BondSortRequest> bondSorts;
    /**
     * Диапазон текущей цены в рублях: цена облигаций в иностранной валюте переводится в рубли по курсу ЦБ.
     * Облигации, для которых цены или курса нет, фильтр не отсекает, но они идут в конце выдачи.
     */
    private ValueRangeRequest currentPrice;
    /**
     * Диапазон текущей цены в процентах от номинала - не зависит от валюты облигации.
     * Облигации без текущей цены фильтр не отсекает, но они идут в конце выдачи.
     */
    private ValueRangeRequest percentagePrice;
    private List<RiskLevelDto> riskLevels;
    private Boolean isOfz;
    /**
     * Тип купона: true - только постоянный (фиксированный), false - только переменный (флоатеры), null - любой.
     */
    private Boolean isFixedCoupon;
    /**
     * Дата погашения в диапазоне (включительно). Облигация без даты погашения при заданном фильтре не подходит.
     */
    private DateRangeRequest maturityDate;
    /** Секторы T-Invest (government, financial, ...), без учёта регистра; облигация без сектора не подходит */
    private List<String> sectors;
    /** Лет до погашения (дни / 365); облигация без даты погашения не подходит */
    private ValueRangeRequest yearsToMaturity;
    /** Выплат купона в год (1, 2, 4, 12...) - любое из списка */
    private List<Integer> couponFrequencies;
    /** true - только с будущей офертой, false - только без, null - все */
    private Boolean hasOffer;
    /** true - только с амортизацией, false - только без, null - все */
    private Boolean hasAmortization;
    /** Кредитный рейтинг (общая шкала АКРА и НКР); облигация без рейтинга не подходит */
    @Valid
    private RatingRangeRequest creditRating;
    /** Дюрация Маколея, лет; облигация без дюрации не подходит */
    private ValueRangeRequest duration;
    /** Доходность к погашению, % годовых; облигация без доходности не подходит */
    private ValueRangeRequest yieldToMaturity;
    /** Средний дневной оборот за 30 дней, рублей; облигация без свечей не подходит */
    private ValueRangeRequest avgDailyTurnover;
    /** Спред к ОФЗ, п.п.; облигация без спреда не подходит */
    private ValueRangeRequest spreadToOfz;
}
