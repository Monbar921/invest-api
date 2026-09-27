package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.annotation.Active;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.usecase.PriceSyncUseCase;
import ru.invest.api.tinkoff.supplier.provider.TinkoffPriceProvider;
import ru.invest.api.tinkoff.supplier.usecase.CouponInterestUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffPriceUseCase;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Active
public class TinkoffPriceSyncUseCaseImpl implements PriceSyncUseCase {
    private final TinkoffPriceProvider tinkoffPriceProvider;
    private final TinkoffBondUseCase tinkoffBondUseCase;
    private final TinkoffPriceUseCase tinkoffPriceUseCase;
    private final CouponInterestUseCase couponInterestUseCase;

    @Override
    public void syncAll(final AuditModel audit) {
        final Map<String, BondModel> bonds = tinkoffBondUseCase.getAll()
                .stream()
                .filter(Objects::nonNull)
                .filter(bond -> StringUtils.isNotBlank(bond.getUid()))
                .collect(Collectors.toMap(BondModel::getUid, Function.identity()));

        syncPrice(bonds, audit);
    }

    @Override
    public void syncByTicker(final String ticker, final AuditModel audit) {
        final BondModel bond = tinkoffBondUseCase.findByTicker(ticker);

        syncPrice(Map.of(bond.getUid(), bond), audit);
    }

    private void syncPrice(final Map<String, BondModel> bonds, final AuditModel audit) {
        if (MapUtils.isEmpty(bonds)) {
            return;
        }

        final Map<String, PriceModel> prices = tinkoffPriceProvider.getLastPrices(bonds, audit);

        tinkoffPriceUseCase.saveCurrentPrices(prices);

        // доходность купона считается от текущей цены, поэтому пересчитываем её после сохранения цен
        if (MapUtils.isNotEmpty(prices)) {
            couponInterestUseCase.recalculate(prices.keySet(), audit);
        }
    }
}
