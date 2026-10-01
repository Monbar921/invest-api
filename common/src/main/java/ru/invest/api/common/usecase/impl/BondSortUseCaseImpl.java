package ru.invest.api.common.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.mapper.BondParametersMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.enums.RiskLevel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.model.parameters.ValueRangeModel;
import ru.invest.api.common.usecase.BondSortUseCase;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static ru.invest.api.common.predicates.BondModelPredicates.OFZ_PREDICATE;

@Service
@RequiredArgsConstructor
public class BondSortUseCaseImpl implements BondSortUseCase {
    private final BondParametersMapper bondParametersMapper;

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
                .sorted(priceUnknownLast(bondParameters).thenComparing(bondParameters.getComparator()));
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
