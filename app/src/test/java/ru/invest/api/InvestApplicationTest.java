package ru.invest.api;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.dto.dto.bond.BondDto;
import ru.invest.api.starter.client.InvestApiBondClient;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class InvestApplicationTest extends AbstractInvestApplicationTest {
//    @MockitoBean
//    private BudgetOrgClient budgetOrgClient;
//    @MockitoBean
//    private CbRfClient cbRfClient;
//    @MockitoBean
//    private InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsServiceBlockingStub;
//    @MockitoBean
//    private MarketDataServiceGrpc.MarketDataServiceBlockingStub marketDataServiceBlockingStub;

    @Autowired
    private TinkoffBondUseCase tinkoffBondUseCase;
    @Autowired
    private InvestApiBondClient investApiBondClient;

    @Test
    public void runApplicationTest() {
    }

    @Test
    @Disabled
    public void realCall() {
        final List<BondModel> bonds = tinkoffBondUseCase.getForeignCurrencyBonds();
        assertThat(!bonds.isEmpty(), is(true));
    }

    @Test
    @Disabled
    public void clientCall() {
        final List<BondDto> bonds = investApiBondClient.getAllForeignBonds(null);
        assertThat(!bonds.isEmpty(), is(true));
    }
}
