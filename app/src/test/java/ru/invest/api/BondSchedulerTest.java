package ru.invest.api;

import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.bond.supplier.scheduler.BondSyncScheduler;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.usecase.BondSyncUseCase;

import java.util.List;

public class BondSchedulerTest extends AbstractInvestApplicationTest {
    @Autowired
    private List<BondSyncUseCase> bondSyncUseCases;
    @Autowired
    private AuditMapper auditMapper;

    private BondSyncScheduler bondSyncScheduler;

    @PostConstruct
    public void setup() {
        this.bondSyncScheduler = new BondSyncScheduler(bondSyncUseCases, auditMapper);
    }

    @Test
    public void syncAllBondsTest() {
        bondSyncScheduler.syncBonds();
    }

}
