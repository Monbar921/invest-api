package ru.invest.api.instrument.usecase;

import ru.tinkoff.piapi.contract.v1.InstrumentRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

public interface InstrumentUseCase {
    InstrumentRequest getInstrumentRequest(String ticker, String classCode);

    SyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> getStubWrapper();
}
