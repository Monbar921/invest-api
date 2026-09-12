package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.invest.api.common.annotation.Active;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.Pair;
import ru.invest.api.tinkoff.supplier.provider.TinkoffCouponProvider;
import ru.invest.api.tinkoff.supplier.usecase.CouponSyncUseCase;
import ru.invest.api.tinkoff.supplier.usecase.GetNeedToUpdateCouponsUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponRepositoryUseCase;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static ru.invest.api.tinkoff.supplier.constants.Constants.COUPON_EXECUTOR_SERVICE;

@Slf4j
@Service
@RequiredArgsConstructor
@Active
public class TinkoffCouponSyncUseCaseImpl implements CouponSyncUseCase {
    private static final int BATCH_SIZE = 100;

    private final TinkoffCouponProvider tinkoffCouponProvider;
    private final TinkoffCouponRepositoryUseCase tinkoffCouponRepositoryUseCase;
    private final GetNeedToUpdateCouponsUseCase getNeedToUpdateCouponsUseCase;

    @Qualifier(COUPON_EXECUTOR_SERVICE)
    private final ExecutorService couponExecutorService;

    @Override
    public void syncAll(final AuditModel audit) {
        final List<Pair<String, String>> uidTickers = getNeedToUpdateCouponsUseCase.getUidTickersToUpdate();

        if (CollectionUtils.isEmpty(uidTickers)) {
            return;
        }

        final Iterator<String> iterator = tickers.stream()
                .filter(StringUtils::isNotBlank)
                .iterator();

        final Map<String, List<CouponDataModel>> couponBatch = new HashMap<>(BATCH_SIZE);

        while (iterator.hasNext()) {
            String ticker = iterator.next();
            couponBatch.put(ticker, List.of());

            if (couponBatch.size() == BATCH_SIZE || !iterator.hasNext()) {
                fetchCouponsAsyncAndGet(couponBatch, audit);
                updateEntity(couponBatch);
                couponBatch.clear();
            }
        }
    }

    private void fetchCouponsAsyncAndGet(final Map<String, List<CouponDataModel>> batchCouponData, final AuditModel audit) {
        final List<CompletableFuture<Void>> futures = batchCouponData.keySet().stream()
                .map(ticker -> CompletableFuture.runAsync(
                        () -> batchCouponData.put(ticker, tinkoffCouponProvider.getCouponData(ticker, audit)),
                        couponExecutorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void updateEntity(final Map<String, List<CouponDataModel>> couponBatch) {
        if (MapUtils.isEmpty(couponBatch)) {
            return;
        }

        tinkoffCouponRepositoryUseCase.saveCouponData(couponBatch);
    }
}
