package ru.invest.api.tinkoff.supplier.wrapper.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.invest.api.tinkoff.supplier.wrapper.MarketDataGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;
import ru.ttech.piapi.core.connector.resilience.ResilienceSyncStubWrapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataGrpcRateLimitedWrapperImpl implements MarketDataGrpcRateLimitedWrapper {

    private final ResilienceSyncStubWrapper<MarketDataServiceGrpc.MarketDataServiceBlockingStub> marketDataService;

    @Override
    public GetLastPricesResponse getLastPrices(final GetLastPricesRequest request) {
        return marketDataService.callSyncMethod(MarketDataServiceGrpc.getGetLastPricesMethod(), stub -> stub.getLastPrices(request));
    }
}
