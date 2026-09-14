package ru.invest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BondSchedulerTest extends AbstractInvestApplicationTest {
    @Autowired
    private BondSyn couponSyncUseCase;

    @Test
    public void runApplicationTest() {
    }

}
