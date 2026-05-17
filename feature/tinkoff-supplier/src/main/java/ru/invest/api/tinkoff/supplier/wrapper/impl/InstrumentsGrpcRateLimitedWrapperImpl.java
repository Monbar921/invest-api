package ru.invest.api.tinkoff.supplier.wrapper.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.invest.api.tinkoff.supplier.ratelimiter.service.RateLimiterService;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.BondsResponse;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentsRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;

@Slf4j
@Component
@RequiredArgsConstructor
public class InstrumentsGrpcRateLimitedWrapperImpl implements InstrumentsGrpcRateLimitedWrapper {

    private final InstrumentsServiceGrpc.InstrumentsServiceBlockingStub stub;
    private final RateLimiterService rateLimiterService;

    @Override
    public BondsResponse bonds(final InstrumentsRequest request) {
        return rateLimiterService.executeWithRateLimitAsyncAndGet(() -> stub.bonds(request));
    }

    @Override
    public GetBondCouponsResponse getBondCoupons(final GetBondCouponsRequest request) {
        return rateLimiterService.executeWithRateLimitAsyncAndGet(() -> stub.getBondCoupons(request));
    }
}
