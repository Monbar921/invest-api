package ru.invest.api.ui.service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.usecase.CouponSyncUseCase;

import static org.springframework.http.HttpHeaders.FROM;

@RestController
@RequestMapping("/internal/rest/maintenance/coupon")
@RequiredArgsConstructor
public class CouponMaintenanceController {
    private final CouponSyncUseCase couponSyncUseCase;
    private final AuditMapper auditMapper;


    @PostMapping("/sync-all")
    public void syncAll(@RequestHeader(FROM) final String user) {
        couponSyncUseCase.syncAll(
                auditMapper.toCurrentAuditModel(user)
        );
    }

    @PostMapping("/sync-by-ticker/{ticker}")
    public void syncByTicker(@RequestHeader(FROM) final String user, @PathVariable final String ticker) {
        couponSyncUseCase.syncByTicker(ticker, auditMapper.toCurrentAuditModel(user));
    }
}
