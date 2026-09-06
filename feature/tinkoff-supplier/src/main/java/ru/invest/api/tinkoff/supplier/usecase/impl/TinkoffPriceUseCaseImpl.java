package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.tinkoff.supplier.mapper.PriceMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffPriceUseCase;
import ru.invest.api.tinkoff.supplier.wrapper.MarketDataGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;
import ru.tinkoff.piapi.contract.v1.LastPrice;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.invest.api.common.constants.CacheConstants.PRICE_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.PRICE_CACHE_NAME;

@Component
@RequiredArgsConstructor
public class TinkoffPriceUseCaseImpl implements TinkoffPriceUseCase {
    private final MarketDataGrpcRateLimitedWrapper marketDataServiceBlockingStub;

    private final PriceMapper priceMapper;

    @Override
    @Cacheable(cacheManager = PRICE_CACHE_MANAGER, cacheNames = PRICE_CACHE_NAME)
    public Map<String, PriceModel> getLastPrices(final Map<String, BondModel> bonds) {
        if (MapUtils.isEmpty(bonds)) {
            return Collections.emptyMap();
        }

        final GetLastPricesRequest request = GetLastPricesRequest.newBuilder()
                .addAllInstrumentId(bonds.keySet())
                .build();

        final GetLastPricesResponse response = marketDataServiceBlockingStub.getLastPrices(request);

        return response.getLastPricesList().stream()
                .filter(Objects::nonNull)
                .map(lastPrice -> toModel(bonds, lastPrice))
                .collect(Collectors.toMap(PriceModel::getUid, Function.identity()));
    }

    private PriceModel toModel(
            final Map<String, BondModel> bonds,
            final LastPrice lastPrice) {

        final BondModel bondModel = bonds.get(lastPrice.getInstrumentUid());

        final MoneyModel moneyModel = Optional.ofNullable(bondModel)
                .map(BondModel::getPrice)
                .map(PriceModel::getNominal)
                .orElse(null);

        return priceMapper.toBondPriceModel(lastPrice, moneyModel);
    }
}
