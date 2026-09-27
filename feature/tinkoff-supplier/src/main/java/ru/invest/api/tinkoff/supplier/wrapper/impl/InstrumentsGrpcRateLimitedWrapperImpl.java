package ru.invest.api.tinkoff.supplier.wrapper.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.BondsResponse;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsRequest;
import ru.tinkoff.piapi.contract.v1.GetBondCouponsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentsRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.ttech.piapi.core.connector.resilience.ResilienceSyncStubWrapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstrumentsGrpcRateLimitedWrapperImpl implements InstrumentsGrpcRateLimitedWrapper {

    private final ResilienceSyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> instrumentsService;

    @Override
    public BondsResponse bonds(final InstrumentsRequest request) {
        return instrumentsService.callSyncMethod(InstrumentsServiceGrpc.getBondsMethod(), stub -> stub.bonds(request));
    }

    @Override
    public GetBondCouponsResponse getBondCoupons(final GetBondCouponsRequest request) {
        return instrumentsService.callSyncMethod(InstrumentsServiceGrpc.getGetBondCouponsMethod(), stub -> stub.getBondCoupons(request));
    }
}
