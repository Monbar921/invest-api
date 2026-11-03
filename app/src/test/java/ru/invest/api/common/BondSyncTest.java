package ru.invest.api.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.Price;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.usecase.BondSyncUseCase;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondApiMapper;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.BondsResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

@Sql("/sql/clean-up.sql")
public class BondSyncTest extends AbstractInvestApplicationTest {
    @Autowired
    private BondSyncUseCase bondSyncUseCase;
    @Autowired
    private AuditMapper auditMapper;
    @Autowired
    private TinkoffBondApiMapper tinkoffBondApiMapper;
    @Autowired
    private BondRepository bondRepository;
    @Autowired
    private InstrumentsGrpcRateLimitedWrapper instrumentsGrpcWrapper;


    @BeforeEach
    public void setUp(){

    }

    @Test
    public void syncAllBondsTest() {
        final BondsResponse fixture = loadFixture();

        when(instrumentsGrpcWrapper.bonds(any()))
                .thenReturn(fixture);

        final Map<String, BondModel> expectedByUid = fixture.getInstrumentsList()
                .stream()
                .collect(Collectors.toMap(
                        ru.tinkoff.piapi.contract.v1.Bond::getUid,
                        tinkoffBondApiMapper::toModel,
                        (first, second) -> first
                ));

        bondSyncUseCase.syncAll(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));

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
            assertThat(actual.getOfferDate(), equalTo(expected.getOfferDate()));
            assertThat(actual.isAmortizationFlag(), equalTo(expected.isAmortizationFlag()));

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
            assertThat(actualPrice.getAciCurrency(), equalTo(expected.getPrice().getAci().getCurrency()));
            // numeric(19,2) в БД округляет значение половиной вверх
            assertThat(actualPrice.getAciValue(),
                    comparesEqualTo(expected.getPrice().getAci().getQuantity().setScale(2, RoundingMode.HALF_UP)));
            // текущая цена при сохранении облигации не заполняется - её пишет синхронизация цен
            assertThat(actualPrice.getPrice(), nullValue());
            assertThat(actualPrice.getCurrency(), nullValue());

            assertThat(actualPrice.getCreated(), notNullValue());
            assertThat(actualPrice.getCreated().getCommittedBy(), equalTo(SCHEDULER_PROCESS));
            assertThat(actualPrice.getCreated().getCommittedAt(), notNullValue());
        }
    }

    @Test
    public void syncOfferAciAndAmortizationTest() {
        when(instrumentsGrpcWrapper.bonds(any()))
                .thenReturn(loadFixture());

        bondSyncUseCase.syncAll(auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS));

        // call_date в фикстуре - 1799798400
        final Bond withOffer = bondRepository.findByTicker("RU000A10C5B8").orElseThrow();
        assertThat(withOffer.getOfferDate(), equalTo(LocalDateTime.of(2027, 1, 13, 0, 0)));
        assertThat(withOffer.isAmortizationFlag(), equalTo(false));

        final Bond withAmortization = bondRepository.findByTicker("RU000A10DZH4").orElseThrow();
        assertThat(withAmortization.getOfferDate(), nullValue());
        assertThat(withAmortization.isAmortizationFlag(), equalTo(true));

        final Price price = bondRepository.findByTicker("RU000A10EQ34").orElseThrow().getPrice();
        assertThat(price.getAciValue(), comparesEqualTo(new BigDecimal("11.72")));
        assertThat(price.getAciCurrency(), equalTo("rub"));
    }
}
