package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.invest.api.common.annotation.Active;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.ShortProductModel;
import ru.invest.api.common.usecase.CouponSyncUseCase;
import ru.invest.api.tinkoff.supplier.provider.TinkoffCouponProvider;
import ru.invest.api.tinkoff.supplier.usecase.GetNeedToUpdateCouponsUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponUseCase;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
    private final TinkoffCouponUseCase tinkoffCouponUseCase;
    private final GetNeedToUpdateCouponsUseCase getNeedToUpdateCouponsUseCase;
    private final TinkoffBondUseCase tinkoffBondUseCase;
    private final TinkoffBondCacheRepositoryUseCase tinkoffBondCacheRepositoryUseCase;

    @Qualifier(COUPON_EXECUTOR_SERVICE)
    private final ExecutorService couponExecutorService;

    @Override
    public void syncAll(final AuditModel audit) {
        syncCoupons(getNeedToUpdateCouponsUseCase.getUidTickersToUpdate(), audit);
    }

    @Override
    public void syncByTicker(final String ticker, final AuditModel audit) {
        final BondModel bond = tinkoffBondUseCase.findByTicker(ticker);

        final ShortProductModel shortProductModel = new ShortProductModel().setUid(bond.getUid()).setTicker(bond.getTicker());
        syncCoupons(List.of(shortProductModel), audit);
    }

    private void syncCoupons(final List<ShortProductModel> uidTickers, final AuditModel audit) {
        if (CollectionUtils.isEmpty(uidTickers)) {
            return;
        }

        final List<ShortProductModel> validUidTickers = uidTickers.stream()
                .filter(Objects::nonNull)
                .filter(uidTicker -> StringUtils.isNotBlank(uidTicker.getTicker()))
                .filter(uidTicker -> StringUtils.isNotBlank(uidTicker.getUid()))
                .toList();

        if (CollectionUtils.isEmpty(validUidTickers)) {
            return;
        }

        final Map<String, String> uidTickerMap = validUidTickers.stream()
                .collect(Collectors.toMap(ShortProductModel::getUid, ShortProductModel::getTicker));

        log.info("Processing coupons, total size is {}", validUidTickers.size());
        try {
            ListUtils.partition(validUidTickers, BATCH_SIZE)
                    .forEach(batch -> updateEntity(fetchCouponsAsyncAndGet(batch, uidTickerMap, audit), uidTickerMap));
        } finally {
            tinkoffBondCacheRepositoryUseCase.evictAll();
        }
    }

    /**
     * Графики купонов пачки; облигации, по которым API ответил ошибкой, в результат не попадают -
     * их прежний график остаётся, а остальные облигации пачки загружаются.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private Map<String, List<CouponDataModel>> fetchCouponsAsyncAndGet(final List<ShortProductModel> batch,
                                                                       final Map<String, String> uidTickerMap,
                                                                       final AuditModel audit) {
        // результаты пишут потоки пула
        final Map<String, List<CouponDataModel>> batchCouponData = new ConcurrentHashMap<>(batch.size());

        final List<CompletableFuture<Void>> futures = batch.stream()
                .map(ShortProductModel::getUid)
                .map(uid -> CompletableFuture.runAsync(() -> {
                    try {
                        batchCouponData.put(uid, tinkoffCouponProvider.getCouponData(uid, uidTickerMap.get(uid), audit));
                    } catch (final RuntimeException e) {
                        log.warn("Failed to fetch coupons for bond {}", uidTickerMap.get(uid), e);
                    }
                }, couponExecutorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        return batchCouponData;
    }

    /**
     * Пачка сохраняется одной транзакцией; если она упала, облигации сохраняются по одной, чтобы ошибка в данных
     * одной облигации не оставила без графиков всю пачку.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void updateEntity(final Map<String, List<CouponDataModel>> couponDataBatch, final Map<String, String> uidTickerMap) {
        if (MapUtils.isEmpty(couponDataBatch)) {
            return;
        }

        try {
            tinkoffCouponUseCase.saveCouponData(couponDataBatch);
        } catch (final RuntimeException exception) {
            log.warn("Failed to save coupons batch of {} bonds, saving one by one", couponDataBatch.size(), exception);

            couponDataBatch.forEach((uid, couponData) -> {
                try {
                    tinkoffCouponUseCase.saveCouponData(Map.of(uid, couponData));
                } catch (final RuntimeException e) {
                    log.error("Failed to save coupons for bond {}", uidTickerMap.get(uid), e);
                }
            });
        }
    }
}
