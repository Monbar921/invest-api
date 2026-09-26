package ru.invest.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Price;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondApiMapper;
import ru.tinkoff.piapi.contract.v1.BondsResponse;
import ru.tinkoff.piapi.contract.v1.InstrumentsServiceGrpc;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

public class BondSyncTest extends AbstractInvestApplicationTest {
    @Autowired
    private BondUseCase bondUseCase;
    @Autowired
    private AuditMapper auditMapper;
    @Autowired
    private TinkoffBondApiMapper tinkoffBondApiMapper;
    @Autowired
    private BondRepository bondRepository;
    @Autowired
    private InstrumentsServiceGrpc.InstrumentsServiceBlockingStub instrumentsServiceBlockingStub;

    @Test
    public void syncAllBondsTest() {
        final BondsResponse fixture = loadFixture();

        when(instrumentsServiceBlockingStub.bonds(any()))
                .thenReturn(fixture);

        final Map<String, BondModel> expectedByUid = fixture.getInstrumentsList()
                .stream()
                .collect(Collectors.toMap(
                        ru.tinkoff.piapi.contract.v1.Bond::getUid,
                        tinkoffBondApiMapper::toModel,
                        (first, second) -> first
                ));

        bondUseCase.syncAll(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));

        final List<Bond> actualBonds = bondRepository.findByUidIn(expectedByUid.keySet());

        assertThat(actualBonds, hasSize(expectedByUid.size()));

        for (final Bond actual : actualBonds) {
            assertThat(expectedByUid, hasKey(actual.getUid()));

            final BondModel expected = expectedByUid.get(actual.getUid());

            assertThat(actual.getTicker(), equalTo(expected.getTicker()));
            assertThat(actual.getIsin(), equalTo(expected.getIsin()));
            assertThat(actual.getName(), equalTo(expected.getName()));
            assertThat(actual.getSector(), equalTo(expected.getSector()));
            assertThat(actual.getCurrency(), equalTo(expected.getCurrency()));
            assertThat(actual.getRiskLevel(), equalTo(expected.getRiskLevel()));
            assertThat(actual.getMaturityDate(), equalTo(expected.getMaturityDate()));

            assertThat(actual.getCreated(), notNullValue());
            assertThat(actual.getCreated().getCommittedBy(), equalTo(SCHEDULER_PROCESS));
            assertThat(actual.getCreated().getCommittedAt(), notNullValue());

            final Price actualPrice = actual.getPrice();

            assertThat(actualPrice, notNullValue());
            assertThat(actualPrice.getTicker(), equalTo(expected.getTicker()));
            assertThat(actualPrice.getUid(), equalTo(expected.getUid()));
            assertThat(actualPrice.getNominalCurrency(), equalTo(expected.getPrice().getNominal().getCurrency()));
            // BigDecimal.equals() чувствителен к scale (2 в БД против nano-точности в модели),
            // поэтому сравниваем значение, а не представление
            assertThat(actualPrice.getNominalPrice(), comparesEqualTo(expected.getPrice().getNominal().getQuantity()));
            // текущая цена при сохранении облигации не заполняется - её пишет синхронизация цен
            assertThat(actualPrice.getPrice(), nullValue());
            assertThat(actualPrice.getCurrency(), nullValue());

            assertThat(actualPrice.getCreated(), notNullValue());
            assertThat(actualPrice.getCreated().getCommittedBy(), equalTo(SCHEDULER_PROCESS));
            assertThat(actualPrice.getCreated().getCommittedAt(), notNullValue());
        }
    }
}
