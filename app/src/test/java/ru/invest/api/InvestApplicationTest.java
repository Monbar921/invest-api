package ru.invest.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.invest.api.budget.org.supplier.client.feign.BudgetOrgClient;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParameters;
import ru.invest.api.tinkoff.supplier.usecase.BondUseCase;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@SpringBootTest
public class InvestApplicationTest {
    @MockitoBean
    private BudgetOrgClient budgetOrgClient;
    @MockitoBean
    private CbRfClient cbRfClient;
    @MockitoBean
    private InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsServiceBlockingStub;
    @MockitoBean
    private MarketDataServiceGrpc.MarketDataServiceBlockingStub marketDataServiceBlockingStub;

    @Autowired
    private BondUseCase bondUseCase;

    @Test
    public void runApplicationTest() {
    }

    @Test
    @Disabled
    public void realCall() {
        final BondParameters parameters = new BondParameters().setBatchLimit(10);
        final List<BondModel> bonds = bondUseCase.getForeignCurrencyBonds(parameters);
        assertThat(!bonds.isEmpty(), is(true));
    }
}
