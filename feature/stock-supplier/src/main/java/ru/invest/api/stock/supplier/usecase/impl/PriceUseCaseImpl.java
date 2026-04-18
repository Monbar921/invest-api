package ru.invest.api.stock.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.BigDecimalMapper;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.stock.supplier.mapper.PriceMapper;
import ru.invest.api.stock.supplier.usecase.PriceUseCase;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PriceUseCaseImpl implements PriceUseCase {
    private final SyncStubWrapper<MarketDataServiceGrpc.MarketDataServiceBlockingStub> marketDataServiceBlockingStub;

    private final PriceMapper priceMapper;
    private final BigDecimalMapper bigDecimalMapper;

    @Override
    public Map<String, PriceModel> getBondPrice(final Map<String, Bond> bonds) {
        if (MapUtils.isEmpty(bonds)) {
            return Collections.emptyMap();
        }

        final List<String> uids = bonds.entrySet()
                .stream()
                .filter(Objects::nonNull)
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .map(Bond::getUid)
                .toList();

        final GetLastPricesRequest request = GetLastPricesRequest.newBuilder()
                .addAllInstrumentId(uids)
                .build();

        final GetLastPricesResponse response = marketDataServiceBlockingStub.getStub()
                .getLastPrices(request);
        return response.getLastPricesList().stream()
                .filter(Objects::nonNull)
                .map(lastPrice -> getPrice(lastPrice, bonds))
                .collect(Collectors.toMap(PriceModel::getUid, Function.identity()));
    }

    private PriceModel getPrice(final LastPrice lastPrice, final Map<String, Bond> bonds) {
        if (lastPrice == null) {
            return null;
        }

        final BigDecimal nominalPercentage = bigDecimalMapper.fromBaseAndNanoFloatParts(lastPrice.getPrice().getUnits(),
                lastPrice.getPrice().getNano());

        final Bond bond = Optional.ofNullable(bonds.get(lastPrice.getInstrumentUid()))
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.NOT_FOUND
                        , "Bond is not present while calculating current price"));

        return priceMapper.toModel(bond, nominalPercentage);
    }
}
