package ru.invest.api.common.usecase.impl;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondSortField;
import ru.invest.api.common.model.parameters.BondSortModel;
import ru.invest.api.common.model.parameters.BondSortOrder;
import ru.invest.api.common.usecase.BondComparatorUseCase;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

@Component
public class BondComparatorUseCaseImpl implements BondComparatorUseCase {
    private static final Comparator<BondModel> DEFAULT_COMPARATOR = Comparator.comparing(
            bond -> bond.getPrice() != null && bond.getPrice().getCurrent() != null
                    && bond.getPrice().getCurrent().getQuantity() != null
                    ? bond.getPrice().getCurrent().getQuantity() : null,
            Comparator.nullsLast(Comparator.reverseOrder()));

    @Override
    public Comparator<BondModel> createComparator(final List<BondSortModel> bondSorts) {
        final List<Comparator<BondModel>> comparators = new LinkedList<>();

        if (CollectionUtils.isNotEmpty(bondSorts)) {
            bondSorts
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(bondSort -> bondSort.getSortField() != null)
                    .forEach(bondSort -> {
                        Comparator<BondModel> comparator = buildComparator(bondSort.getSortField());
                        if (bondSort.getSortOrder() == BondSortOrder.DESC) {
                            comparator = comparator.reversed();
                        }
                        comparators.add(comparator);
                    });
        }

        if (CollectionUtils.isEmpty(comparators)) {
            comparators.add(DEFAULT_COMPARATOR);
        }

        return comparators.stream()
                .reduce(Comparator::thenComparing)
                .orElse((o1, o2) -> 0);
    }

    private Comparator<BondModel> buildComparator(final BondSortField sortField) {
        return switch (sortField) {
            case RISK_LEVEL -> Comparator.comparingInt(this::riskPriority);
            case COUPON_INTEREST -> Comparator.comparing(
                    bond -> bond.getCoupon() != null ? bond.getCoupon().getInterest() : null,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case MATURITY_DATE -> Comparator.comparing(
                    BondModel::getMaturityDate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case TICKER -> Comparator.comparing(
                    BondModel::getTicker,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case NAME -> Comparator.comparing(
                    BondModel::getName,
                    Comparator.nullsLast(Comparator.naturalOrder()));
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
