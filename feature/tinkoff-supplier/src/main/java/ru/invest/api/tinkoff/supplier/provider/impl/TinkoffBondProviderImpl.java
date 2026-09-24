package ru.invest.api.tinkoff.supplier.provider.impl;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondApiMapper;
import ru.invest.api.tinkoff.supplier.provider.TinkoffBondProvider;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.BondsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentStatus;
import ru.tinkoff.piapi.contract.v1.InstrumentsRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.invest.api.common.constants.CacheConstants.BOND_TINKOFF_API_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.BOND_TINKOFF_API_CACHE_NAME;

@Service
@RequiredArgsConstructor
public class TinkoffBondProviderImpl implements TinkoffBondProvider {
    private final InstrumentsGrpcRateLimitedWrapper instrumentsGrpcRateLimitedWrapper;

    private final TinkoffBondApiMapper tinkoffBondApiMapper;

    @Override
    @Cacheable(cacheNames = BOND_TINKOFF_API_CACHE_NAME, cacheManager = BOND_TINKOFF_API_CACHE_MANAGER)
    public Map<String, BondModel> getAllBonds() {
        final List<Bond> filteredBonds = filterDuplicates(
                filterDuplicates(getAllBondsDto(), Bond::getUid), Bond::getTicker
        );

        return filteredBonds
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Bond::getUid, tinkoffBondApiMapper::toModel));
    }

    private List<Bond> getAllBondsDto() {
        final InstrumentsRequest bondsRequest = InstrumentsRequest.newBuilder()
                .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                .build();

        final BondsResponse response = instrumentsGrpcRateLimitedWrapper
                .bonds(bondsRequest);

        return response.getInstrumentsList();
    }

    @SneakyThrows
    private void dumpFixture(final List<Bond> originalBonds)  {
        final BondsResponse response = BondsResponse.newBuilder()
                .addAllInstruments(originalBonds.stream().limit(20).toList())
                .build();

        Files.writeString(
                Path.of("app/src/test/resources/fixtures/bonds-response.pbtxt"),
                com.google.protobuf.TextFormat.printer().printToString(response)
        );
    }

    private <T> List<Bond> filterDuplicates(final List<Bond> bonds, final Function<Bond, T> duplicateFieldGetter) {
        if (CollectionUtils.isEmpty(bonds)) {
            return Collections.emptyList();
        }

        return bonds
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        duplicateFieldGetter,      // ключ
                        p -> p,             // значение
                        (first, second) -> first
                ))
                .values().stream()
                .toList();
    }
}
