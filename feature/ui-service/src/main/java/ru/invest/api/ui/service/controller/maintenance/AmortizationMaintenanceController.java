package ru.invest.api.ui.service.controller.maintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.usecase.AmortizationSyncUseCase;

import static org.springframework.http.HttpHeaders.FROM;

@RestController
@RequestMapping("/internal/rest/maintenance/bond/amortization")
@RequiredArgsConstructor
public class AmortizationMaintenanceController {
    private final AmortizationSyncUseCase amortizationSyncUseCase;
    private final AuditMapper auditMapper;
    private final MaintenanceTaskRunner maintenanceTaskRunner;

    /**
     * Полная синхронизация в фоне: 202 - запущена, 409 - уже идёт. Ход и итог - в логе приложения.
     */
    @PostMapping("/sync-all")
    public ResponseEntity<ProblemDetail> syncAll(@RequestHeader(FROM) final String user) {
        final AuditModel audit = auditMapper.toCurrentAuditModel(user);
        return maintenanceTaskRunner.start("amortization sync", () -> amortizationSyncUseCase.syncAll(audit));
    }

    @PostMapping("/sync-by-ticker/{ticker}")
    public void syncByTicker(@RequestHeader(FROM) final String user, @PathVariable final String ticker) {
        amortizationSyncUseCase.syncByTicker(ticker, auditMapper.toCurrentAuditModel(user));
    }
}
