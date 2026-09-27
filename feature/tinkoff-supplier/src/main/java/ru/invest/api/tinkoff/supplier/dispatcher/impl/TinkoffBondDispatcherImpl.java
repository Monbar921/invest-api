package ru.invest.api.tinkoff.supplier.dispatcher.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.parameters.BondParametersModel;
import ru.invest.api.common.usecase.BondFilterUseCase;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffBondDispatcher;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffCouponDispatcher;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondEntityMapper;
import ru.invest.api.tinkoff.supplier.provider.TinkoffPriceProvider;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TinkoffBondDispatcherImpl implements TinkoffBondDispatcher {
    private final TinkoffBondEntityMapper bondEntityMapper;

    private final TinkoffPriceProvider tinkoffPriceProvider;
    private final TinkoffBondCacheRepositoryUseCase tinkoffBondCacheUseCase;
    private final TinkoffCouponDispatcher tinkoffCouponDispatcher;
    private final BondFilterUseCase bondFilterUseCase;

    @Override
    public List<BondModel> getForeignCurrencyBonds(final BondParametersModel bondParameters) {
        return getBonds(tinkoffBondCacheUseCase::getForeignCurrencyBonds, bondParameters);
    }

    @Override
    public List<BondModel> getRubbleCurrencyBonds(final BondParametersModel bondParameters) {
        return getBonds(tinkoffBondCacheUseCase::getRubbleCurrencyBonds, bondParameters);
    }

    @Override
    public List<BondModel> getAll(final BondParametersModel bondParameters) {
        return getBonds(tinkoffBondCacheUseCase::getBonds, bondParameters);
    }

    private List<BondModel> getBonds(final Supplier<Map<String, BondModel>> bondModelMapSupplier,
                                     final BondParametersModel bondParameters) {
        final Map<String, BondModel> bondModelMap = bondModelMapSupplier.get();

        if (MapUtils.isEmpty(bondModelMap)) {
            return Collections.emptyList();
        }

        //TODO Нужно пытаться получить цену и если не получили, то сходить в базу и вытащить оттуда
        final Map<String, PriceModel> bondPrices = tinkoffPriceProvider.getLastPrices(bondModelMap, null);
        final List<BondModel> bondModels = bondEntityMapper.enrichBonds(bondModelMap, bondPrices);

        enrichByCoupons(bondModels);

        return bondFilterUseCase.filter(bondModels, bondParameters);
    }

    private void enrichByCoupons(final List<BondModel> bondModels) {
        if (CollectionUtils.isEmpty(bondModels)) {
            return;
        }

        final List<CouponModel> coupons = tinkoffCouponDispatcher.getCoupons(bondModels);
        if (CollectionUtils.isEmpty(coupons)) {
            return;
        }

        final Map<String, CouponModel> couponsMap = coupons
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(CouponModel::getUid, Function.identity()));

        bondModels
                .stream()
                .filter(Objects::nonNull)
                .forEach(bondModel -> bondModel.setCoupon(couponsMap.get(bondModel.getUid())));
    }
}
