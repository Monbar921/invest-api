package ru.invest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.tinkoff.supplier.provider.TinkoffBondProvider;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class TinkoffBondProviderTest extends AbstractInvestApplicationTest {

    @Autowired
    private TinkoffBondProvider tinkoffBondProvider;
    @Autowired
    private InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsServiceBlockingStub;

    @Test
    public void getAllBonds_mapsRealFixture() {
        when(instrumentsServiceBlockingStub.bonds(any()))
                .thenReturn(loadFixture());

        final Map<String, BondModel> bonds = tinkoffBondProvider.getAllBonds();

        assertThat(bonds).isNotEmpty();
    }
}
