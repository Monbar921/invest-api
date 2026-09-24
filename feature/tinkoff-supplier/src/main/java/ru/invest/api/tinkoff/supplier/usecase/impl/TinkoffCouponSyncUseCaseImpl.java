package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.invest.api.common.annotation.Active;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.CouponDataModel;
import ru.invest.api.common.model.ShortProductModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.usecase.CouponSyncUseCase;
import ru.invest.api.tinkoff.supplier.provider.TinkoffCouponProvider;
import ru.invest.api.tinkoff.supplier.usecase.GetNeedToUpdateCouponsUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponRepositoryUseCase;

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
    private static final String BOND_NOT_FOUND_MESSAGE = "Bond not found for ticker %s";

    private final TinkoffCouponProvider tinkoffCouponProvider;
    private final TinkoffCouponRepositoryUseCase tinkoffCouponRepositoryUseCase;
    private final GetNeedToUpdateCouponsUseCase getNeedToUpdateCouponsUseCase;
    private final BondRepository bondRepository;

    @Qualifier(COUPON_EXECUTOR_SERVICE)
    private final ExecutorService couponExecutorService;

    @Override
    public void syncAll(final AuditModel audit) {
        syncCoupons(getNeedToUpdateCouponsUseCase.getUidTickersToUpdate(), audit);
    }

    @Override
    public void syncByTicker(final String ticker, final AuditModel audit) {
        final Bond bond = bondRepository.findByTicker(ticker)
                .orElseThrow(() -> new GeneralNotFoundEntityException(
                        ExceptionErrorCode.BOND_NOT_FOUND, BOND_NOT_FOUND_MESSAGE.formatted(ticker)));

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

        final Iterator<ShortProductModel> uidTickerIterator = validUidTickers.iterator();
        final Map<String, List<CouponDataModel>> couponBatch = new HashMap<>(BATCH_SIZE);

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

    private void updateEntity(final Map<String, List<CouponDataModel>> couponDataBatch) {
        if (MapUtils.isEmpty(couponDataBatch)) {
            return;
        }

        tinkoffCouponRepositoryUseCase.saveCouponData(couponDataBatch);
    }
}
