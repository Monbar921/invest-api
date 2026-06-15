package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.invest.api.tinkoff.supplier.usecase.BondRetrieverUseCase;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.BondsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentStatus;
import ru.tinkoff.piapi.contract.v1.InstrumentsRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.invest.api.common.constants.CacheConstants.BOND_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.BOND_CACHE_NAME;

@Component
@RequiredArgsConstructor
public class BondRetrieverUseCaseImpl implements BondRetrieverUseCase {
    private final InstrumentsGrpcRateLimitedWrapper instrumentsGrpcRateLimitedWrapper;

    @Override
    @Cacheable(value = BOND_CACHE_NAME, cacheManager = BOND_CACHE_MANAGER)
    public Map<String, Bond> getAllBonds() {
        return getAllBondsDto()
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Bond::getUid, Function.identity()));
    }

    private List<Bond> getAllBondsDto() {
        final InstrumentsRequest bondsRequest = InstrumentsRequest.newBuilder()
                .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                .build();

        final BondsResponse response = instrumentsGrpcRateLimitedWrapper
                .bonds(bondsRequest);

        return response.getInstrumentsList();
    }
}
