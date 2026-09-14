package ru.invest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.mapper.AuditMapper;

import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

public class BondSyncTest extends AbstractInvestApplicationTest {
    @Autowired
    private BondUseCase bondUseCase;
    @Autowired
    private AuditMapper auditMapper;

    @Test
    public void syncAllBondsTest() {
        bondUseCase.syncAll(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));
    }

}
