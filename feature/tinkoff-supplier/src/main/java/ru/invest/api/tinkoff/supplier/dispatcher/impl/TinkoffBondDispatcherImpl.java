package ru.invest.api.tinkoff.supplier.dispatcher.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.BondParametersMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.model.parameters.BondSortField;
import ru.invest.api.common.model.parameters.BondSortModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.usecase.BondSortUseCase;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffBondDispatcher;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffCouponDispatcher;
import ru.invest.api.tinkoff.supplier.mapper.BondMapper;
import ru.invest.api.tinkoff.supplier.mapper.MoneyMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffPriceUseCase;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.invest.api.tinkoff.supplier.constants.Constants.COUPON_EXECUTOR_SERVICE;
import static ru.invest.api.tinkoff.supplier.predicates.BondCurrencyPredicates.FOREIGN_CURRENCIES;
import static ru.invest.api.tinkoff.supplier.predicates.BondCurrencyPredicates.RU_CURRENCIES;

@Component
@RequiredArgsConstructor
public class TinkoffBondDispatcherImpl implements TinkoffBondDispatcher {
    private static final Set<BondSortField> EXCLUDED_SORT_FIELDS = Set.of(BondSortField.COUPON_INTEREST);

    private final BondMapper bondMapper;
    private final MoneyMapper moneyMapper;
    private final BondParametersMapper bondParametersMapper;

    private final BondRepository bondRepository;
    private final TinkoffPriceUseCase tinkoffPriceUseCase;
    private final TinkoffCouponDispatcher tinkoffCouponDispatcher;
    private final BondSortUseCase bondSortUseCase;

    @Qualifier(COUPON_EXECUTOR_SERVICE)
    private final ExecutorService couponExecutorService;

    @Override
    public List<BondModel> getForeignCurrencyBonds(final BondParametersModel bondParameters) {
        return getBonds(FOREIGN_CURRENCIES, bondParameters);
    }

    @Override
    public List<BondModel> getRubbleCurrencyBonds(final BondParametersModel bondParameters) {
        return getBonds(RU_CURRENCIES, bondParameters);
    }

    private List<BondModel> getBonds(final Set<String> currencies, final BondParametersModel bondParameters) {
        final List<Bond> bonds = bondRepository.findByCurrencyIn(currencies);

        if (CollectionUtils.isEmpty(bonds)) {
            return Collections.emptyList();
        }

        final Map<String, Bond> bondsByUid = bonds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Bond::getUid, Function.identity()));

        final List<String> uids = List.copyOf(bondsByUid.keySet());

        final Map<String, PriceModel> bondPrices = tinkoffPriceUseCase.getLastPrices(uids, bondsByUid, getNominalPrice());

        final List<BondModel> bondModels = bondMapper.toModelFromEntities(bondsByUid, bondPrices);

        enrichWithCouponsAsync(bondModels);

        return getFilteredBonds(bondParameters, bondModels);
    }

    private void enrichWithCouponsAsync(final List<BondModel> bondModels) {
        final List<CompletableFuture<Void>> futures = bondModels.stream()
                .filter(Objects::nonNull)
                .map(bondModel -> CompletableFuture.runAsync(() -> bondModel.setCoupon(tinkoffCouponDispatcher.getCoupon(bondModel)), couponExecutorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private BiFunction<Map<String, Bond>, String, MoneyValue> getNominalPrice() {
        return (bonds, uid) -> {
            if (uid == null || uid.isBlank()) {
                return null;
            }

            final Bond bond = Optional.ofNullable(bonds.get(uid))
                    .orElseThrow(() -> new GeneralNotFoundEntityException(
                            ExceptionErrorCode.NOT_FOUND,
                            "Bond is not present while calculating current price"));

            return moneyMapper.toMoneyValue(bond.getNominalPrice(), bond.getNominalCurrency());
        };
    }

    private List<BondModel> getFilteredBonds(final BondParametersModel bondParameters, final List<BondModel> bonds) {
        if (CollectionUtils.isEmpty(bonds)) {
            return bonds;
        }

        final List<BondSortModel> sorts = Optional.ofNullable(bondParameters)
                .map(BondParametersModel::getBondSorts)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(sort -> sort.getSortField() != null
                        && !EXCLUDED_SORT_FIELDS.contains(sort.getSortField()))
                .toList();

        final BondParametersModel actualizedParameters = bondParametersMapper.toModel(bondParameters, sorts);

        return bondSortUseCase.getFilteredBonds(actualizedParameters, bonds);
    }

}
