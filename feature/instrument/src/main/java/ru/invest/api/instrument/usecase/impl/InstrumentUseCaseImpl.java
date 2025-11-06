package ru.invest.api.instrument.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.invest.api.instrument.usecase.InstrumentUseCase;
import ru.tinkoff.piapi.contract.v1.InstrumentIdType;
import ru.tinkoff.piapi.contract.v1.InstrumentRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class InstrumentUseCaseImpl implements InstrumentUseCase {
    private final ServiceStubFactory serviceStubFactory;

    public InstrumentRequest getInstrumentRequest(final String ticker, final String classCode) {
        return InstrumentRequest.newBuilder()
                .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_TICKER)
                .setId(ticker)
                .setClassCode(classCode)
                .build();
    }

    public SyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> getStubWrapper() {
        return serviceStubFactory.newSyncService(InstrumentsServiceGrpc::newBlockingStub);
    }
}
