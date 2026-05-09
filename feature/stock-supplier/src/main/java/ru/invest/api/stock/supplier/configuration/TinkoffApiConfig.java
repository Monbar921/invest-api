package ru.invest.api.stock.supplier.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

@Configuration
@Slf4j
public class TinkoffApiConfig {

    @Bean
    public InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsServiceBlockingStub(
            final ServiceStubFactory serviceStubFactory) {
        return serviceStubFactory.newSyncService(InstrumentsServiceGrpc::newBlockingStub).getStub();
    }

    @Bean
    public SyncStubWrapper<MarketDataServiceGrpc.MarketDataServiceBlockingStub> marketDataServiceBlockingStub(
            final ServiceStubFactory serviceStubFactory) {
        return serviceStubFactory.newSyncService(MarketDataServiceGrpc::newBlockingStub);
    }
}
