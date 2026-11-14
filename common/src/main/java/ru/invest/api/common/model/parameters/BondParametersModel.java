package ru.invest.api.common.model.parameters;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.enums.RiskLevel;

import java.util.Comparator;
import java.util.List;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class BondParametersModel {
    private List<BondSortModel> bondSorts;
    private Comparator<BondModel> comparator;
    private ValueRangeModel currentPrice;
    private ValueRangeModel percentagePrice;
    private List<RiskLevel> riskLevels;
    private Boolean isOfz;
    /** true - только постоянный купон, false - только переменный, null - любой */
    private Boolean isFixedCoupon;
    /** Дата погашения в диапазоне; облигация без даты погашения при заданном фильтре не подходит */
    private DateRangeModel maturityDate;
    /** Сектор T-Invest (без учёта регистра) - любой из списка; облигация без сектора не подходит */
    private List<String> sectors;
    /** Лет до погашения (дни / 365); облигация без даты погашения не подходит */
    private ValueRangeModel yearsToMaturity;
    /** Выплат купона в год - любое из списка; облигация без данных о купоне не подходит */
    private List<Integer> couponFrequencies;
    /** true - только с будущей офертой, false - только без, null - все */
    private Boolean hasOffer;
    /** true - только с амортизацией, false - только без, null - все */
    private Boolean hasAmortization;
    /** Ступени рейтинга; облигация без рейтинга не подходит */
    private RatingRangeModel creditRating;
    /** Дюрация Маколея, лет; без дюрации - не подходит */
    private ValueRangeModel duration;
    /** Доходность к погашению, % годовых; без доходности - не подходит */
    private ValueRangeModel yieldToMaturity;
    /** Средний дневной оборот за 30 дней в рублях; без оборота - не подходит */
    private ValueRangeModel avgDailyTurnover;
    /** Спред к ОФЗ, п.п.; без спреда - не подходит */
    private ValueRangeModel spreadToOfz;
    private Integer batchLimit;
}
