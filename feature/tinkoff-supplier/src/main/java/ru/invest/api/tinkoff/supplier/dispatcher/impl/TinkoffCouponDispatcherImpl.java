package ru.invest.api.tinkoff.supplier.dispatcher.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.CouponModel;
import ru.invest.api.tinkoff.supplier.dispatcher.TinkoffCouponDispatcher;
import ru.invest.api.tinkoff.supplier.service.CouponCalculationService;
import ru.invest.api.tinkoff.supplier.provider.TinkoffCouponProvider;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCouponRepositoryUseCase;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static ru.invest.api.tinkoff.supplier.constants.Constants.COUPON_EXECUTOR_SERVICE;

@Service
@RequiredArgsConstructor
public class TinkoffCouponDispatcherImpl implements TinkoffCouponDispatcher {
    private final TinkoffCouponProvider tinkoffCouponProvider;
    private final TinkoffCouponRepositoryUseCase tinkoffCouponRepositoryUseCase;
    private final CouponCalculationService couponCalculationService;

    @Qualifier(COUPON_EXECUTOR_SERVICE)
    private final ExecutorService couponExecutorService;

    @Override
    public List<CouponModel> getCoupons(final List<BondModel> bonds) {
        if (CollectionUtils.isEmpty(bonds)) {
            return Collections.emptyList();
        }

        final CouponModel coupon = dispatchCoupon(bondModel);

        return coupon.setInterest(
                couponCalculationService.calculateInterest(coupon, bondModel)
        );
    }

    private void enrichWithCouponsAsync(final List<BondModel> bondModels) {
        final List<CompletableFuture<Void>> futures = bondModels.stream()
                .filter(Objects::nonNull)
                .map(bondModel -> CompletableFuture.runAsync(() ->
                        bondModel.setCoupon(tinkoffCouponDispatcher.getCoupons(bondModel)), couponExecutorService))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    public CouponModel dispatchCoupon(final BondModel bondModel) {
        if (!needTryFetchFromDatabase(bondModel)) {
            return fetchCouponsFromTinkoffApi(bondModel);
        }

        return Optional.ofNullable(fetchCouponsFromDatabase(bondModel))
                .filter(databaseCoupon -> CollectionUtils.isNotEmpty(databaseCoupon.getCouponData()))
                .orElseGet(() -> fetchCouponsFromTinkoffApi(bondModel));
    }

    private CouponModel fetchCouponsFromDatabase(final BondModel bondModel) {
        return tinkoffCouponRepositoryUseCase.getCoupon(bondModel);
    }

    private CouponModel fetchCouponsFromTinkoffApi(final BondModel bondModel) {
        return tinkoffCouponProvider.getCoupon(bondModel);
    }

    private boolean needTryFetchFromDatabase(final BondModel bondModel) {
        return Optional.ofNullable(bondModel.getCoupon())
                .map(CouponModel::getIsFixed)
                .orElse(false);
    }
}
