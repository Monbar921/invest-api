package ru.invest.api.bond.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.model.parameters.BondSortField;
import ru.invest.api.common.model.parameters.BondSortModel;
import ru.invest.api.common.model.parameters.BondSortOrder;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BondUseCaseImpl implements BondUseCase {
    private static final int DEFAULT_BATCH_LIMIT = 50;
    private static final Comparator<BondModel> DEFAULT_COMPARATOR = Comparator.comparing(
            bond -> bond.getCoupon() != null ? bond.getCoupon().getInterest() : null,
            Comparator.nullsLast(Comparator.reverseOrder()));

    private final TinkoffBondUseCase tinkoffBondUseCase;

    @Override
    public List<BondModel> getForeignCurrencyBonds(final BondParametersModel bondParametersModel) {
        final List<BondModel> bonds = tinkoffBondUseCase.getForeignCurrencyBonds();

        if (CollectionUtils.isEmpty(bonds)) {
            return bonds;
        }

        final List<BondSortModel> bondSorts = Optional.ofNullable(bondParametersModel)
                .map(BondParametersModel::getBondSorts)
                .orElse(Collections.emptyList());
        final Integer batchLimit = Optional.ofNullable(bondParametersModel)
                .map(BondParametersModel::getBatchLimit)
                .orElse(DEFAULT_BATCH_LIMIT);

        final Comparator<BondModel> comparator = createComparator(bondSorts);

        return bonds.stream()
                .filter(Objects::nonNull)
                .sorted(comparator)
                .limit(batchLimit)
                .toList();
    }

    public Comparator<BondModel> createComparator(final List<BondSortModel> bondSorts) {

        final List<Comparator<BondModel>> comparators = new LinkedList<>();

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

        if (CollectionUtils.isEmpty(comparators)) {
            comparators.add(DEFAULT_COMPARATOR);
        }

        return comparators.stream()
                .reduce(Comparator::thenComparing)
                .orElse((o1, o2) -> 0);
    }

    private Comparator<BondModel> buildComparator(final BondSortField sortField) {
        return switch (sortField) {
            case RISK_LEVEL -> Comparator.comparingInt(BondUseCaseImpl::riskPriority);
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
    private static int riskPriority(final BondModel bond) {
        if (bond.getRiskLevel() == null) {
            return 2;
        }
        return switch (bond.getRiskLevel()) {
            case RISK_LEVEL_LOW, RISK_LEVEL_MODERATE -> 0;
            default -> 1;
        };
    }
}
