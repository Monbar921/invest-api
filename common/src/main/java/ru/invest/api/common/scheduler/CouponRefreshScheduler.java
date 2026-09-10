package ru.invest.api.common.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.common.usecase.CouponSyncUseCase;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.stock.supplier.scheduler.coupon", name = "enabled", havingValue = "true")
public class CouponRefreshScheduler {
    private final List<CouponSyncUseCase> couponSyncUseCases;

    @Scheduled(cron = "${ru.invest.api.stock.supplier.scheduler.coupon.cron}")
    public void refreshCoupons() {
        log.info("Coupon refresh scheduler started");
        couponSyncUseCases
                .stream()
                .findAny()
                .ifPresent(CouponSyncUseCase::syncAll);
        log.info("Coupon refresh scheduler finished");
    }
}
