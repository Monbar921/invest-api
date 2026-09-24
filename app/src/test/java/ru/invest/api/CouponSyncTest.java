package ru.invest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.CouponRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondApiMapper;
import ru.tinkoff.piapi.contract.v1.BondsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

public class CouponSyncTest extends AbstractInvestApplicationTest {
    @Autowired
    private BondUseCase bondUseCase;
    @Autowired
    private AuditMapper auditMapper;
    @Autowired
    private TinkoffBondApiMapper tinkoffBondApiMapper;
    @Autowired
    private CouponRepository couponRepository;
    @Autowired
    private InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsServiceBlockingStub;

    @Test
    public void syncAllCouponsTest() {

    }
}
