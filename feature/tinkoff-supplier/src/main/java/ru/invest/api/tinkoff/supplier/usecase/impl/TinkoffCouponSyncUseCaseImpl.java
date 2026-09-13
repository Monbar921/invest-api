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
import ru.invest.api.common.model.ShortProductModel;
import ru.invest.api.tinkoff.supplier.provider.TinkoffCouponProvider;
import ru.invest.api.tinkoff.supplier.usecase.CouponSyncUseCase;
import ru.invest.api.tinkoff.supplier.usecase.GetNeedToUpdateCouponsUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponDataRepositoryUseCase;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static ru.invest.api.tinkoff.supplier.constants.Constants.COUPON_EXECUTOR_SERVICE;

@Slf4j
@Service
@RequiredArgsConstructor
@Active
public class TinkoffCouponSyncUseCaseImpl implements CouponSyncUseCase {
    private static final int BATCH_SIZE = 100;

    private final TinkoffCouponProvider tinkoffCouponProvider;
    private final TinkoffCouponDataRepositoryUseCase tinkoffCouponDataRepositoryUseCase;
    private final GetNeedToUpdateCouponsUseCase getNeedToUpdateCouponsUseCase;

    @Qualifier(COUPON_EXECUTOR_SERVICE)
    private final ExecutorService couponExecutorService;

    @Override
    public void syncAll(final AuditModel audit) {
        final List<ShortProductModel> uidTickers = getNeedToUpdateCouponsUseCase.getUidTickersToUpdate();

        if (CollectionUtils.isEmpty(uidTickers)) {
            return;
        }

        final Iterator<ShortProductModel> uidTickerIterator = uidTickers.stream()
                .filter(Objects::nonNull)
                .filter(uidTicker -> StringUtils.isNotBlank(uidTicker.getTicker()))
                .filter(uidTicker -> StringUtils.isNotBlank(uidTicker.getUid()))
                .iterator();

        final Map<String, List<CouponDataModel>> couponBatch = new HashMap<>(BATCH_SIZE);

        final Map<String, String> uidTickerMap = uidTickers
                .stream()
                .filter(Objects::nonNull)
                .filter(uidTicker -> StringUtils.isNotBlank(uidTicker.getTicker()))
                .filter(uidTicker -> StringUtils.isNotBlank(uidTicker.getUid()))
                .collect(Collectors.toMap(ShortProductModel::getUid, ShortProductModel::getTicker));

        while (uidTickerIterator.hasNext()) {
            final ShortProductModel uidTicker = uidTickerIterator.next();
            couponBatch.put(uidTicker.getUid(), List.of());

            if (couponBatch.size() == BATCH_SIZE || !uidTickerIterator.hasNext()) {
                fetchCouponsAsyncAndGet(couponBatch, uidTickerMap, audit);
                updateEntity(couponBatch);
                couponBatch.clear();
            }
        }
    }

    private void fetchCouponsAsyncAndGet(final Map<String, List<CouponDataModel>> batchCouponData, final Map<String, String> uidTickerMap,
                                         final AuditModel audit) {
        final List<CompletableFuture<Void>> futures = batchCouponData.keySet().stream()
                .map(uid -> CompletableFuture.runAsync(
                        () -> batchCouponData.put(uid, tinkoffCouponProvider.getCouponData(uid, uidTickerMap.get(uid), audit)),
                        couponExecutorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void updateEntity(final Map<String, List<CouponDataModel>> couponBatch) {
        if (MapUtils.isEmpty(couponBatch)) {
            return;
        }

        tinkoffCouponDataRepositoryUseCase.saveCouponData(couponBatch);
    }
}
