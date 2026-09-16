package ru.invest.api.ui.service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.usecase.BondSyncUseCase;

import static org.springframework.http.HttpHeaders.FROM;

@RestController("/internal/rest/maintenance")
@RequiredArgsConstructor
public class BondMaintenanceController {
    private final BondSyncUseCase bondSyncUseCase;
    private final AuditMapper auditMapper;


    @PostMapping("/sync-all")
    public void syncAll(@RequestHeader(FROM) final String user) {
        bondSyncUseCase.syncAll(
                auditMapper.toCurrentAuditModel(user)
        );
    }
}
