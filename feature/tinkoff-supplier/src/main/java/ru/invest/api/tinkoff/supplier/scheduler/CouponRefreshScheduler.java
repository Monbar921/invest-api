package ru.invest.api.tinkoff.supplier.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.tinkoff.supplier.usecase.CouponSyncUseCase;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.stock.supplier.scheduler.coupon", name = "enabled", havingValue = "true")
public class CouponRefreshScheduler {
    private final CouponSyncUseCase couponSyncUseCase;

    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.coupon.cron}")
    public void refreshCoupons() {
        log.info("Coupon refresh scheduler started");
        couponSyncUseCase.syncAll();
        log.info("Coupon refresh scheduler finished");
    }
}
