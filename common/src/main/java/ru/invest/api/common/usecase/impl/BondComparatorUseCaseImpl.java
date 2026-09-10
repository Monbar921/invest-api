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
                .orElse((o1, o2) -> 0);
    }

    private <T extends Comparable<? super T>> Comparator<T> getOrder(final BondSortOrder bondSortOrder) {
        return bondSortOrder == BondSortOrder.DESC
                ? Comparator.reverseOrder()
                : Comparator.naturalOrder();
    }

    private Comparator<BondModel> buildComparator(final BondSortField sortField, final BondSortOrder bondSortOrder) {
        return switch (sortField) {
            case RISK_LEVEL -> Comparator.comparingInt(this::riskPriority);
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
            case PRICE -> Comparator.comparing(
                    bond -> bond.getPrice() != null && bond.getPrice().getCurrent() != null
                            ? bond.getPrice().getCurrent().getQuantity() : null,
                    Comparator.nullsLast(getOrder(bondSortOrder)));
        };
    }

    /**
     * LOW/MODERATE → 0 (лучший риск), остальные → 1, null → 2.
     * Используется при сортировке по RISK_LEVEL (ASC = сначала самые надёжные).
     */
    private int riskPriority(final BondModel bond) {
        if (bond.getRiskLevel() == null) {
            return 2;
        }
        return switch (bond.getRiskLevel()) {
            case RISK_LEVEL_LOW, RISK_LEVEL_MODERATE -> 0;
            default -> 1;
        };
    }
}
