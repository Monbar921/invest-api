package ru.invest.api.common.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.mapper.BondParametersMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.enums.RiskLevel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.model.parameters.DateRangeModel;
import ru.invest.api.common.model.parameters.RatingRangeModel;
import ru.invest.api.common.model.parameters.ValueRangeModel;
import ru.invest.api.common.model.rating.RatingScale;
import ru.invest.api.common.usecase.BondSortUseCase;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.invest.api.common.predicates.BondModelPredicates.OFZ_PREDICATE;

@Service
@RequiredArgsConstructor
public class BondSortUseCaseImpl implements BondSortUseCase {
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);
    private static final int YEARS_SCALE = 6;

    private final BondParametersMapper bondParametersMapper;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public List<BondModel> getFilteredBonds(final BondParametersModel bondParameters, final List<BondModel> bonds) {
        if (CollectionUtils.isEmpty(bonds)) {
            return bonds;
        }

        final BondParametersModel actualizedParameters = bondParametersMapper.toModel(bondParameters);

        return filterAndSort(actualizedParameters, bonds)
                .limit(actualizedParameters.getBatchLimit())
                .toList();
    }

    @Override
    public List<BondModel> getAllFilteredBonds(final BondParametersModel bondParameters, final List<BondModel> bonds) {
        if (CollectionUtils.isEmpty(bonds)) {
            return List.of();
        }

        return filterAndSort(bondParametersMapper.toModel(bondParameters), bonds).toList();
    }

    private Stream<BondModel> filterAndSort(final BondParametersModel bondParameters, final List<BondModel> bonds) {
        return bonds.stream()
                .filter(Objects::nonNull)
                .filter(bond -> filterByValueRange(bondParameters.getCurrentPrice(), getCurrentPrice(bond)))
                .filter(bond -> filterByValueRange(bondParameters.getPercentagePrice(), getPercentagePrice(bond)))
                .filter(bond -> filterByRiskLevel(bondParameters.getRiskLevels(), bond.getRiskLevel()))
                .filter(bond -> filterByOfz(bondParameters.getIsOfz(), bond))
                .filter(bond -> filterByFixedCoupon(bondParameters.getIsFixedCoupon(), bond))
                .filter(bond -> filterByMaturityDate(bondParameters.getMaturityDate(), bond))
                .filter(screenerFilter(bondParameters))
                .sorted(priceUnknownLast(bondParameters).thenComparing(bondParameters.getComparator()));
    }

    /**
     * Фильтры скринера: облигация без значения (нет рейтинга, дюрации, оборота) при заданном фильтре не подходит -
     * про неё нельзя сказать, что значение в диапазоне. Этим они отличаются от фильтров цены, которые пропускают
     * облигации без цены в конец выдачи: цена появляется после синхронизации, рейтинга может не быть вовсе.
     */
    private Predicate<BondModel> screenerFilter(final BondParametersModel parameters) {
        final LocalDate today = dateTimeMapper.getNow().toLocalDate();
        final Set<String> sectors = Optional.ofNullable(parameters.getSectors()).orElseGet(List::of).stream()
                .filter(StringUtils::isNotBlank)
                .map(sector -> sector.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        final Integer minScore = ratingBound(parameters.getCreditRating(), RatingRangeModel::getMin);
        final Integer maxScore = ratingBound(parameters.getCreditRating(), RatingRangeModel::getMax);
        final boolean ratingActive = minScore != null || maxScore != null;

        return bond -> (sectors.isEmpty() || bond.getSector() != null
                && sectors.contains(bond.getSector().toLowerCase(Locale.ROOT)))
                && inRange(parameters.getYearsToMaturity(), yearsToMaturity(bond, today))
                && (CollectionUtils.isEmpty(parameters.getCouponFrequencies()) || bond.getCoupon() != null
                && parameters.getCouponFrequencies().contains(bond.getCoupon().getQuantityPerYear()))
                && (parameters.getHasOffer() == null || parameters.getHasOffer() == hasOffer(bond, today))
                && (parameters.getHasAmortization() == null || parameters.getHasAmortization() == bond.isAmortizationFlag())
                && (!ratingActive || inScoreRange(BondScreenerValues.ratingScore(bond), minScore, maxScore))
                && inRange(parameters.getDuration(), BondScreenerValues.duration(bond))
                && inRange(parameters.getYieldToMaturity(), BondScreenerValues.yieldToMaturity(bond))
                && inRange(parameters.getAvgDailyTurnover(), BondScreenerValues.avgDailyTurnoverRub(bond))
                && inRange(parameters.getSpreadToOfz(), bond.getSpreadToOfz());
    }

    private Integer ratingBound(final RatingRangeModel range, final Function<RatingRangeModel, String> bound) {
        return Optional.ofNullable(range).map(bound).flatMap(RatingScale::score).orElse(null);
    }

    private boolean inScoreRange(final Integer score, final Integer min, final Integer max) {
        return score != null && (min == null || score >= min) && (max == null || score <= max);
    }

    /**
     * Оферта есть, если её дата не в прошлом: прошедшая оферта уже ничего не даёт держателю.
     */
    private boolean hasOffer(final BondModel bond, final LocalDate today) {
        return bond.getOfferDate() != null && !bond.getOfferDate().toLocalDate().isBefore(today);
    }

    private BigDecimal yearsToMaturity(final BondModel bond, final LocalDate today) {
        if (bond.getMaturityDate() == null) {
            return null;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(today, bond.getMaturityDate().toLocalDate()))
                .divide(DAYS_IN_YEAR, YEARS_SCALE, RoundingMode.HALF_UP);
    }

    private boolean inRange(final ValueRangeModel range, final BigDecimal value) {
        if (!isActive(range)) {
            return true;
        }
        return value != null && comparePrices(value, range.getMin(), 1) && comparePrices(value, range.getMax(), -1);
    }

    /**
     * Облигации без цены проходят фильтр по цене (она ещё не синхронизирована), но при заданном фильтре
     * должны идти после облигаций с подходящей ценой - иначе они заняли бы начало батча.
     * Без фильтра по цене порядок не меняется.
     */
    private Comparator<BondModel> priceUnknownLast(final BondParametersModel bondParameters) {
        return Comparator.comparing((BondModel bond) -> isFilteredPriceUnknown(bondParameters, bond));
    }

    private boolean isFilteredPriceUnknown(final BondParametersModel bondParameters, final BondModel bond) {
        return isActive(bondParameters.getCurrentPrice()) && getCurrentPrice(bond) == null
                || isActive(bondParameters.getPercentagePrice()) && getPercentagePrice(bond) == null;
    }

    private boolean isActive(final ValueRangeModel range) {
        return range != null && !ObjectUtils.allNull(range.getMin(), range.getMax());
    }

    private boolean filterByOfz(final Boolean isOfz, final BondModel bond) {
        if (isOfz == null) {
            return true;
        }

        return BooleanUtils.isTrue(isOfz) && OFZ_PREDICATE.test(bond)
                || !BooleanUtils.isTrue(isOfz) && !OFZ_PREDICATE.test(bond);
    }

    /**
     * Облигация без данных о купоне не подходит ни под постоянный, ни под переменный купон.
     */
    private boolean filterByFixedCoupon(final Boolean isFixedCoupon, final BondModel bond) {
        if (isFixedCoupon == null) {
            return true;
        }

        return bond.getCoupon() != null && isFixedCoupon.equals(bond.getCoupon().getIsFixed());
    }

    /**
     * Облигация без даты погашения не подходит ни под какой диапазон: нельзя сказать, что она гасится в нём.
     */
    private boolean filterByMaturityDate(final DateRangeModel range, final BondModel bond) {
        if (range == null || range.getFrom() == null && range.getTo() == null) {
            return true;
        }
        if (bond.getMaturityDate() == null) {
            return false;
        }

        final LocalDate maturity = bond.getMaturityDate().toLocalDate();
        return (range.getFrom() == null || !maturity.isBefore(range.getFrom()))
                && (range.getTo() == null || !maturity.isAfter(range.getTo()));
    }

    private boolean filterByRiskLevel(final List<RiskLevel> riskLevels, final RiskLevel riskLevel) {
        if (riskLevel == null || CollectionUtils.isEmpty(riskLevels)) {
            return true;
        }

        return riskLevels.contains(riskLevel);
    }

    private boolean filterByValueRange(final ValueRangeModel price, final BigDecimal comparedValue) {
        if (!isActive(price)) {
            return true;
        }

        if (comparedValue == null) {
            return true;
        }

        return comparePrices(comparedValue, price.getMin(), 1)
                && comparePrices(comparedValue, price.getMax(), -1);
    }

    // фильтр по текущей цене задаётся в рублях, облигации в других валютах сравниваются по рублёвому эквиваленту
    private BigDecimal getCurrentPrice(final BondModel bondModel) {
        return Optional.ofNullable(bondModel.getPrice())
                .map(PriceModel::getCurrentInRub)
                .orElse(null);
    }

    private BigDecimal getPercentagePrice(final BondModel bondModel) {
        return Optional.ofNullable(bondModel.getPrice())
                .map(PriceModel::getPercentagePrice)
                .orElse(null);
    }

    private boolean comparePrices(final BigDecimal realPrice, final BigDecimal requestedPrice
            , final int conditionMultiplier) {
        if (requestedPrice == null) {
            return true;
        }

        return realPrice.compareTo(requestedPrice) * conditionMultiplier >= 0;
    }
}
