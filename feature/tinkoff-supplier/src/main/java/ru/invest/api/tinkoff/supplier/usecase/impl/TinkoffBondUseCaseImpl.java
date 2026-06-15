package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.tinkoff.supplier.mapper.BondMapper;
import ru.invest.api.tinkoff.supplier.usecase.BondRetrieverUseCase;
import ru.invest.api.tinkoff.supplier.usecase.CouponUseCase;
import ru.invest.api.tinkoff.supplier.usecase.PriceUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static ru.invest.api.tinkoff.supplier.constants.Constants.COUPON_EXECUTOR_SERVICE;
import static ru.invest.api.tinkoff.supplier.predicates.BondPredicates.FOREIGN_CURRENCY_PREDICATE;
import static ru.invest.api.tinkoff.supplier.predicates.BondPredicates.ISIN_PREDICATE;

@Component
@RequiredArgsConstructor
public class TinkoffBondUseCaseImpl implements TinkoffBondUseCase {
    private final BondMapper bondMapper;
    private final PriceUseCase priceUseCase;
    private final BondRetrieverUseCase bondRetrieverUseCase;
    private final CouponUseCase couponUseCase;
    @Qualifier(COUPON_EXECUTOR_SERVICE)
    private final ExecutorService couponExecutorService;

    @Override
    public List<BondModel> getForeignCurrencyBonds() {
        final Map<String, Bond> foreignBonds = filterForeignBonds(bondRetrieverUseCase.getAllBonds());

        if (MapUtils.isEmpty(foreignBonds)) {
            return Collections.emptyList();
        }

        final List<String> uids = foreignBonds.values()
                .stream()
                .filter(Objects::nonNull)
                .map(Bond::getUid)
                .toList();

        final Map<String, PriceModel> bondPrices = priceUseCase.getLastPrices(uids, foreignBonds, getNominalPrice());
        final List<BondModel> bondModels = bondMapper.toModel(foreignBonds, bondPrices);

        enrichWithCouponsAsync(bondModels, foreignBonds);

        return bondModels;
    }

    private void enrichWithCouponsAsync(final List<BondModel> bondModels, final Map<String, Bond> bondsById) {
        final List<CompletableFuture<Void>> futures = bondModels.stream()
                .filter(Objects::nonNull)
                .map(bondModel -> CompletableFuture.runAsync(() -> {
                    final Bond bond = bondsById.get(bondModel.getUid());
                    bondModel.setCoupon(couponUseCase.getCoupons(bondModel, bond));
                }, couponExecutorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private Map<String, Bond> filterForeignBonds(final Map<String, Bond> allBonds) {
        if (MapUtils.isEmpty(allBonds)) {
            return Collections.emptyMap();
        }

        return allBonds.entrySet()
                .stream()
                .filter(Objects::nonNull)
                .filter(entry -> FOREIGN_CURRENCY_PREDICATE.test(entry.getValue()))
                .filter(entry -> ISIN_PREDICATE.test(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static BiFunction<Map<String, Bond>, String, MoneyValue> getNominalPrice() {
        return (bonds, uid) -> {
            if (uid == null || uid.isBlank()) {
                return null;
            }

            final Bond bond = Optional.ofNullable(bonds.get(uid))
                    .orElseThrow(() -> new GeneralNotFoundEntityException(
                            ExceptionErrorCode.NOT_FOUND,
                            "Bond is not present while calculating current price"));

            return bond.getNominal();
        };
    }

}
