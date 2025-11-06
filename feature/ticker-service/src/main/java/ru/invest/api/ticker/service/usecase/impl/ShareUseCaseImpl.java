package ru.invest.api.ticker.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.invest.api.ticker.service.usecase.ShareUseCase;
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentIdType;
import ru.tinkoff.piapi.contract.v1.InstrumentRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.contract.v1.Share;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShareUseCaseImpl implements ShareUseCase {
    private final ServiceStubFactory serviceStubFactory;

    public BigDecimal getSharePrice(final String ticker, final String classCode) {
        final SyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> stubWrapper =
                serviceStubFactory.newSyncService(InstrumentsServiceGrpc::newBlockingStub);

        InstrumentRequest request = InstrumentRequest.newBuilder()
                .setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_TICKER)
                .setId(ticker)
                .setClassCode(classCode)
                .build();

        final Share instrument = stubWrapper.getStub().shareBy(request).getInstrument();

        return getPriceFromShare(instrument);
    }

    private BigDecimal getPriceFromShare(final Share share){
        if(share == null){
            log.warn("Trying to get price from null share");
            return null;
        }

        final SyncStubWrapper<MarketDataServiceGrpc.MarketDataServiceBlockingStub> marketDataStub =
                serviceStubFactory.newSyncService(MarketDataServiceGrpc::newBlockingStub);

        final GetLastPricesRequest priceRequest = GetLastPricesRequest.newBuilder()
                .addInstrumentId(share.getFigi())
                .build();

        final GetLastPricesResponse priceResponse = marketDataStub.getStub().getLastPrices(priceRequest);

        if (priceResponse.getLastPricesCount() == 0) {
            throw new RuntimeException("No price data available for: " + share.getTicker());
        }

        // Get the first (and only) price from response
        final LastPrice lastPrice = priceResponse.getLastPrices(0);

        // Convert Quotation to BigDecimal
        return convertToBigDecimal(lastPrice.getPrice());
    }

    private BigDecimal convertToBigDecimal(Quotation quotation) {
        return BigDecimal.valueOf(quotation.getUnits())
                .add(BigDecimal.valueOf(quotation.getNano(), 9));
    }
}
