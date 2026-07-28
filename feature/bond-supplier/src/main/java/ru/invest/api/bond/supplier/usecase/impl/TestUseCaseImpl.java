package ru.invest.api.bond.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.invest.api.tinkoff.supplier.usecase.BondSyncUseCase;
import ru.invest.api.tinkoff.supplier.usecase.CouponSyncUseCase;

@Component
@RequiredArgsConstructor
public class TestUseCaseImpl {
    private final BondSyncUseCase bondSyncUseCase;
    private final CouponSyncUseCase couponSyncUseCase;

    @EventListener(ApplicationReadyEvent.class)
    public void logDbInfo(final ApplicationReadyEvent event) {
        bondSyncUseCase.syncAll();
        couponSyncUseCase.syncAll();
    }
}
