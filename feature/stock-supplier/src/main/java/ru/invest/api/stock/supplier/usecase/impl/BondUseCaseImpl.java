package ru.invest.api.stock.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.parameters.BondParameters;
import ru.invest.api.stock.supplier.mapper.BondMapper;
import ru.invest.api.stock.supplier.usecase.BondRetrieverUseCase;
import ru.invest.api.stock.supplier.usecase.BondUseCase;
import ru.invest.api.stock.supplier.usecase.PriceUseCase;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static ru.invest.api.stock.supplier.predicates.BondPredicates.FOREIGN_CURRENCY_PREDICATE;
import static ru.invest.api.stock.supplier.predicates.BondPredicates.ISIN_PREDICATE;

@Component
@RequiredArgsConstructor
public class BondUseCaseImpl implements BondUseCase {
    private final BondMapper bondMapper;

    private final PriceUseCase priceUseCase;
    private final BondRetrieverUseCase bondRetrieverUseCase;

    @Override
    public List<BondModel> getForeignCurrencyBonds(final BondParameters bondParameters) {
        final Map<String, Bond> foreignBonds = filterForeignBonds(bondRetrieverUseCase.getAllBonds());

        final List<String> uids = foreignBonds.entrySet()
                .stream()
                .filter(Objects::nonNull)
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .map(Bond::getUid)
                .toList();

        final Map<String, PriceModel> bondPrices = priceUseCase.getLastPrices(uids, foreignBonds, getNominalPrice());
        return bondMapper.toModel(foreignBonds, bondPrices)
                .stream()
                .sorted(bondComparator()).toList();
    }

    private Map<String, Bond> filterForeignBonds(final Map<String, Bond> allBonds) {
        if (MapUtils.isEmpty(allBonds)) {
            return Collections.emptyMap();
        }

        return allBonds
                .entrySet()
                .stream()
                .filter(Objects::nonNull)
                .filter(entry -> FOREIGN_CURRENCY_PREDICATE.test(entry.getValue()))
                .filter(entry -> ISIN_PREDICATE.test(entry.getValue()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
    }

    private static BiFunction<Map<String, Bond>, String, MoneyValue> getNominalPrice() {
        return (bonds, uid) -> {
            if (StringUtils.isBlank(uid)) {
                return null;
            }

            final Bond bond = Optional.ofNullable(bonds.get(uid))
                    .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.NOT_FOUND
                            , "Bond is not present while calculating current price"));

            return bond.getNominal();
        };
    }

    private static Comparator<BondModel> bondComparator() {
        return Comparator.comparingInt(BondUseCaseImpl::getRiskPriority)
                .thenComparing(bond -> {
                    if (bond.getCoupon() == null) {
                        return null;
                    }
                    return bond.getCoupon().getInterest();
                }, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static int getRiskPriority(BondModel bond) {
        if (bond.getRiskLevel() == null) {
            return 2;
        }

        return switch (bond.getRiskLevel()) {
            case RISK_LEVEL_LOW, RISK_LEVEL_MODERATE -> 0;
            default -> 1;
        };
    }

}
