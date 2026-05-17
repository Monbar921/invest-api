package ru.invest.api.tinkoff.supplier.wrapper;

import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;

public interface MarketDataGrpcRateLimitedWrapper {
    GetLastPricesResponse getLastPrices(GetLastPricesRequest request);
}
