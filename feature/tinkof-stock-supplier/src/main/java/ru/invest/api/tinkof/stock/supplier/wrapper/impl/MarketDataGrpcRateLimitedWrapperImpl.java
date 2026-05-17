package ru.invest.api.tinkof.stock.supplier.wrapper.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.invest.api.tinkof.stock.supplier.ratelimiter.service.RateLimiterService;
import ru.invest.api.tinkof.stock.supplier.wrapper.MarketDataGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketDataGrpcRateLimitedWrapperImpl implements MarketDataGrpcRateLimitedWrapper {

    private final MarketDataServiceGrpc.MarketDataServiceBlockingStub stub;
    private final RateLimiterService rateLimiterService;

    @Override
    public GetLastPricesResponse getLastPrices(final GetLastPricesRequest request) {
        return rateLimiterService.executeWithRateLimitAsyncAndGet(() -> stub.getLastPrices(request));
    }
}
