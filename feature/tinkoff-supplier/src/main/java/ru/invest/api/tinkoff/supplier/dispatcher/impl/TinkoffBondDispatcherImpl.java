package ru.invest.api.tinkoff.supplier.dispatcher.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.invest.api.common.mapper.BondParametersMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.model.parameters.BondSortModel;
import ru.invest.api.common.usecase.BondSortUseCase;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffBondDispatcher;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffCouponDispatcher;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondEntityMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffPriceUseCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import static ru.invest.api.tinkoff.supplier.constants.Constants.COUPON_EXECUTOR_SERVICE;

@Service
@RequiredArgsConstructor
public class TinkoffBondDispatcherImpl implements TinkoffBondDispatcher {
    private final BondParametersMapper bondParametersMapper;
    private final TinkoffBondEntityMapper bondEntityMapper;

    private final TinkoffPriceUseCase tinkoffPriceUseCase;
    private final TinkoffBondCacheRepositoryUseCase tinkoffBondCacheUseCase;
    private final TinkoffCouponDispatcher tinkoffCouponDispatcher;
    private final BondSortUseCase bondSortUseCase;

    @Qualifier(COUPON_EXECUTOR_SERVICE)
    private final ExecutorService couponExecutorService;

    @Override
    public List<BondModel> getForeignCurrencyBonds(final BondParametersModel bondParameters) {
        return getBonds(tinkoffBondCacheUseCase::getForeignCurrencyBonds, bondParameters);
    }

    @Override
    public List<BondModel> getRubbleCurrencyBonds(final BondParametersModel bondParameters) {
        return getBonds(tinkoffBondCacheUseCase::getRubbleCurrencyBonds, bondParameters);
    }

    private List<BondModel> getBonds(final Supplier<Map<String, BondModel>> bondModelMapSupplier,
                                     final BondParametersModel bondParameters) {
        final Map<String, BondModel> bondModelMap = bondModelMapSupplier.get();

        if (MapUtils.isEmpty(bondModelMap)) {
            return Collections.emptyList();
        }

        //TODO Нужно пытаться получить цену и если не получили, то сходить в базу и вытащить оттуда
        final Map<String, PriceModel> bondPrices = tinkoffPriceUseCase.getLastPrices(bondModelMap);
        final List<BondModel> bondModels = bondEntityMapper.enrichBonds(bondModelMap, bondPrices);

        enrichWithCouponsAsync(bondModels);

        return getFilteredBonds(bondParameters, bondModels);
    }

    private void enrichWithCouponsAsync(final List<BondModel> bondModels) {
        final List<CompletableFuture<Void>> futures = bondModels.stream()
                .filter(Objects::nonNull)
                .map(bondModel -> CompletableFuture.runAsync(() ->
                        bondModel.setCoupon(tinkoffCouponDispatcher.getCoupon(bondModel)), couponExecutorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
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
                .toList();

        final BondParametersModel actualizedParameters = bondParametersMapper.toModel(bondParameters, sorts);

        return bondSortUseCase.getFilteredBonds(actualizedParameters, bonds);
    }

}
