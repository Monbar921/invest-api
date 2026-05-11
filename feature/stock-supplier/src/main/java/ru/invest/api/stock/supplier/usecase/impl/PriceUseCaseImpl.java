package ru.invest.api.stock.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.common.mapper.BigDecimalMapper;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.stock.supplier.mapper.PriceMapper;
import ru.invest.api.stock.supplier.usecase.PriceUseCase;
import ru.invest.api.stock.supplier.wrapper.MarketDataGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PriceUseCaseImpl implements PriceUseCase {
    private final MarketDataGrpcRateLimitedWrapper marketDataServiceBlockingStub;

    private final PriceMapper priceMapper;
    private final BigDecimalMapper bigDecimalMapper;

    @Override
    public Map<String, PriceModel> getLastPrices(final List<String> uids) {
        return getLastPrices(uids, Collections.emptyMap(), null, null);
    }

    @Override
    public <T> Map<String, PriceModel> getLastPrices(final List<String> uids, final Map<String, T> specificModels,
                                                     final BiFunction<Map<String, T>, String, MoneyValue> nominalGetter,
                                                     final BiFunction<Map<String, T>, String, String> currencyGetter) {
        final GetLastPricesRequest request = GetLastPricesRequest.newBuilder()
                .addAllInstrumentId(uids)
                .build();

        final GetLastPricesResponse response = marketDataServiceBlockingStub.getLastPrices(request);

        return response.getLastPricesList().stream()
                .filter(Objects::nonNull)
                .map(lastPrice -> {
                    final BigDecimal currentPrice = bigDecimalMapper.fromBaseAndNanoFloatParts(lastPrice.getPrice().getUnits(),
                            lastPrice.getPrice().getNano());
                    return priceMapper.toBondPriceModel(lastPrice.getInstrumentUid(),
                            nominalGetter != null ? nominalGetter.apply(specificModels, lastPrice.getInstrumentUid()) : null,
                            currentPrice,
                            currencyGetter != null ? currencyGetter.apply(specificModels, lastPrice.getInstrumentUid()) : null);
                })
                .collect(Collectors.toMap(PriceModel::getUid, Function.identity()));
    }
}
