package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.annotation.Active;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.tinkoff.supplier.usecase.CouponSyncUseCase;
import ru.invest.api.tinkoff.supplier.usecase.GetNeedToUpdateCouponsTickersUseCase;
import ru.invest.api.tinkoff.supplier.provider.TinkoffCouponProvider;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponRepositoryUseCase;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Active
public class TinkoffCouponSyncUseCaseImpl implements CouponSyncUseCase {
    private static final int BATCH_SIZE = 100;

    private final TinkoffCouponProvider tinkoffCouponProvider;
    private final TinkoffCouponRepositoryUseCase tinkoffCouponRepositoryUseCase;
    private final GetNeedToUpdateCouponsTickersUseCase getNeedToUpdateCouponsTickersUseCase;

    @Override
    public void syncAll() {
        final Set<String> tickers = getNeedToUpdateCouponsTickersUseCase.getTickersToUpdate();

        if (CollectionUtils.isEmpty(tickers)) {
            return;
        }

        final Iterator<String> iterator = tickers.stream()
                .filter(StringUtils::isNotBlank)
                .iterator();

        final Map<String, List<CouponDataModel>> couponBatch = new HashMap<>(BATCH_SIZE);

        while (iterator.hasNext()) {
            String ticker = iterator.next();
            couponBatch.put(ticker, tinkoffCouponProvider.getCouponData(ticker));

            if (couponBatch.size() == BATCH_SIZE || !iterator.hasNext()) {
                updateEntity(couponBatch);
                couponBatch.clear();
            }
        }
    }

    private void updateEntity(final Map<String, List<CouponDataModel>> couponBatch) {
        if (MapUtils.isEmpty(couponBatch)) {
            return;
        }

        tinkoffCouponRepositoryUseCase.saveCouponData(couponBatch);
    }
}
