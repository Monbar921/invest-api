package ru.invest.api.stock.supplier.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

@Configuration
public class TinkoffApiConfig {
    @Bean
    public SyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> instrumentsServiceBlockingStub(
            final ServiceStubFactory serviceStubFactory){
        return serviceStubFactory.newSyncService(InstrumentsServiceGrpc::newBlockingStub);
    }
}
