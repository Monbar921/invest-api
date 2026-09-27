package ru.invest.api.ui.service.controller.maintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.usecase.PriceSyncUseCase;

import static org.springframework.http.HttpHeaders.FROM;

@RestController
@RequestMapping("/internal/rest/maintenance/bond/price")
@RequiredArgsConstructor
public class PriceMaintenanceController {
    private final PriceSyncUseCase priceSyncUseCase;
    private final AuditMapper auditMapper;


    @PostMapping("/sync-all")
    public void syncAll(@RequestHeader(FROM) final String user) {
        priceSyncUseCase.syncAll(
                auditMapper.toCurrentAuditModel(user)
        );
    }

    @PostMapping("/sync-by-ticker/{ticker}")
    public void syncByTicker(@RequestHeader(FROM) final String user, @PathVariable final String ticker) {
        priceSyncUseCase.syncByTicker(ticker, auditMapper.toCurrentAuditModel(user));
    }
}
