package ru.invest.api.ui.service.controller.maintenance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.news.supplier.usecase.CreditRatingSyncUseCase;
import ru.invest.api.news.supplier.usecase.NewsSyncUseCase;

import static org.springframework.http.HttpHeaders.FROM;

@RestController
@RequestMapping("/internal/rest/maintenance/news")
@RequiredArgsConstructor
public class NewsMaintenanceController {
    private final NewsSyncUseCase newsSyncUseCase;
    private final CreditRatingSyncUseCase creditRatingSyncUseCase;
    private final AuditMapper auditMapper;
    private final MaintenanceTaskRunner maintenanceTaskRunner;

    /**
     * Загрузка новостей в фоне: 202 - запущена, 409 - уже идёт.
     */
    @PostMapping("/sync-all")
    public ResponseEntity<ProblemDetail> syncAll(@RequestHeader(FROM) final String user) {
        final AuditModel audit = auditMapper.toCurrentAuditModel(user);
        return maintenanceTaskRunner.start("news sync", () -> newsSyncUseCase.syncAll(audit));
    }

    /**
     * Перечитать историю рейтинговых релизов АКРА и НКР за срок действия рейтинга (в фоне: 202 - запущена, 409 - идёт).
     * Нужно, если рейтинги потерялись или поменялись правила разбора; сама загрузка идёт при первом запуске.
     */
    @PostMapping("/ratings-history")
    public ResponseEntity<ProblemDetail> loadRatingHistory(@RequestHeader(FROM) final String user) {
        final AuditModel audit = auditMapper.toCurrentAuditModel(user);
        return maintenanceTaskRunner.start("credit rating history", () -> creditRatingSyncUseCase.loadHistory(audit));
    }
}
