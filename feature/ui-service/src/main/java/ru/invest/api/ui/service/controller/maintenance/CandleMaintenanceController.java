package ru.invest.api.ui.service.controller.maintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.usecase.CandleSyncUseCase;

import static org.springframework.http.HttpHeaders.FROM;

@RestController
@RequestMapping("/internal/rest/maintenance/bond/candle")
@RequiredArgsConstructor
public class CandleMaintenanceController {
    private final CandleSyncUseCase candleSyncUseCase;
    private final AuditMapper auditMapper;

    @PostMapping("/sync-all")
    public void syncAll(@RequestHeader(FROM) final String user) {
        candleSyncUseCase.syncAll(auditMapper.toCurrentAuditModel(user));
    }

    @PostMapping("/sync-by-ticker/{ticker}")
    public void syncByTicker(@RequestHeader(FROM) final String user, @PathVariable final String ticker) {
        candleSyncUseCase.syncByTicker(ticker, auditMapper.toCurrentAuditModel(user));
    }
}
