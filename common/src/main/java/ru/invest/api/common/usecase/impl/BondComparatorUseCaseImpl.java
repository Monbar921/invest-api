package ru.invest.api.common.usecase.impl;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondSortField;
import ru.invest.api.common.model.parameters.BondSortModel;
import ru.invest.api.common.model.parameters.BondSortOrder;
import ru.invest.api.common.usecase.BondComparatorUseCase;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@Service
public class BondComparatorUseCaseImpl implements BondComparatorUseCase {
    // последний ключ сортировки: без него облигации с равными значениями шли бы в порядке кэша,
    // который меняется при его пересборке, и при листании страниц одни повторялись бы, а другие пропадали
    private static final Comparator<BondModel> TIE_BREAKER = Comparator
            .comparing(BondModel::getTicker, Comparator.nullsLast(Comparator.<String>naturalOrder()))
            .thenComparing(BondModel::getUid, Comparator.nullsLast(Comparator.<String>naturalOrder()));

    @Override
    public Comparator<BondModel> createComparator(final List<BondSortModel> bondSorts) {
        final List<Comparator<BondModel>> comparators = new LinkedList<>();

        if (CollectionUtils.isNotEmpty(bondSorts)) {
            bondSorts
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(bondSort -> bondSort.getSortField() != null)
                    .forEach(bondSort -> comparators.add(
                            buildComparator(bondSort.getSortField(), bondSort.getSortOrder())
                    ));
        }

        return comparators.stream()
                .reduce(Comparator::thenComparing)
                .map(comparator -> comparator.thenComparing(TIE_BREAKER))
                .orElse(TIE_BREAKER);
    }

    private <T extends Comparable<? super T>> Comparator<T> getOrder(final BondSortOrder bondSortOrder) {
        return bondSortOrder == BondSortOrder.DESC
                ? Comparator.reverseOrder()
                : Comparator.naturalOrder();
    }

    private Comparator<BondModel> buildComparator(final BondSortField sortField, final BondSortOrder bondSortOrder) {
        return switch (sortField) {
            case RISK_LEVEL -> Comparator.comparing(
                    this::riskPriority,
                    Comparator.nullsLast(getOrder(bondSortOrder)));
            case COUPON_INTEREST -> Comparator.comparing(
                    bond -> bond.getCoupon() != null ? bond.getCoupon().getInterest() : null,
                    Comparator.<BigDecimal>nullsLast(getOrder(bondSortOrder)));
            case MATURITY_DATE -> Comparator.comparing(
                    BondModel::getMaturityDate,
                    Comparator.nullsLast(getOrder(bondSortOrder)));
            case TICKER -> Comparator.comparing(
                    BondModel::getTicker,
                    Comparator.nullsLast(getOrder(bondSortOrder)));
            case NAME -> Comparator.comparing(
                    BondModel::getName,
                    Comparator.nullsLast(getOrder(bondSortOrder)));
            // облигации в разных валютах сравниваются по рублёвому эквиваленту текущей цены
            case PRICE -> Comparator.comparing(
                    bond -> bond.getPrice() != null ? bond.getPrice().getCurrentInRub() : null,
                    Comparator.nullsLast(getOrder(bondSortOrder)));
            case PERCENTAGE_PRICE -> Comparator.comparing(
                    bond -> bond.getPrice() != null ? bond.getPrice().getPercentagePrice() : null,
                    Comparator.nullsLast(getOrder(bondSortOrder)));
        };
    }

    /**
     * Код риска из T-Invest API: LOW 1, MODERATE 2, HIGH 3 (ASC - сначала самые надёжные).
     * Неизвестный риск - null: такие облигации идут в конце при любом направлении.
     */
    private Integer riskPriority(final BondModel bond) {
        if (bond.getRiskLevel() == null) {
            return null;
        }
        return switch (bond.getRiskLevel()) {
            case RISK_LEVEL_LOW, RISK_LEVEL_MODERATE, RISK_LEVEL_HIGH -> bond.getRiskLevel().getValue();
            case RISK_LEVEL_UNSPECIFIED, UNRECOGNIZED -> null;
        };
    }
}
