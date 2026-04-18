package ru.invest.api.stock.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.model.parameters.BondParameters;
import ru.invest.api.stock.supplier.mapper.BondMapper;
import ru.invest.api.stock.supplier.usecase.BondUseCase;
import ru.invest.api.stock.supplier.usecase.PriceUseCase;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.BondsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentStatus;
import ru.tinkoff.piapi.contract.v1.InstrumentsRequest;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ru.invest.api.stock.supplier.predicates.BondPredicates.FOREIGN_CURRENCY_PREDICATE;
import static ru.invest.api.stock.supplier.predicates.BondPredicates.ISIN_PREDICATE;

@Component
@RequiredArgsConstructor
public class BondUseCaseImpl implements BondUseCase {
    private final SyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> instrumentsServiceBlockingStub;

    private final BondMapper bondMapper;

    private final PriceUseCase priceUseCase;

    @Override
    public List<BondModel> getForeignCurrencyBonds(final BondParameters bondParameters) {
        final List<Bond> allBonds = getAllBonds();
        final Map<String, Bond> foreignBonds = filterForeignBonds(allBonds)
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Bond::getUid, Function.identity()));

        final Map<String, PriceModel> bondPrices = priceUseCase.getBondPrice(foreignBonds);
        return bondMapper.toModel(foreignBonds, bondPrices, bondParameters);
    }

    private List<Bond> getAllBonds() {
        final InstrumentsRequest bondsRequest = InstrumentsRequest.newBuilder()
                .setInstrumentStatus(InstrumentStatus.INSTRUMENT_STATUS_BASE)
                .build();

        final BondsResponse response = instrumentsServiceBlockingStub.getStub()
                .bonds(bondsRequest);

        return response.getInstrumentsList();
    }

    private List<Bond> filterForeignBonds(final List<Bond> allBonds) {
        if (CollectionUtils.isEmpty(allBonds)) {
            return Collections.emptyList();
        }

        return allBonds
                .stream()
                .filter(Objects::nonNull)
                .filter(FOREIGN_CURRENCY_PREDICATE)
                .filter(ISIN_PREDICATE)
                .toList();
    }

}
