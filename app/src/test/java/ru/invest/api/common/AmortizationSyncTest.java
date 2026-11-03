package ru.invest.api.common;

import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.common.entity.Amortization;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.repository.AmortizationRepository;
import ru.invest.api.common.usecase.AmortizationSyncUseCase;
import ru.invest.api.tinkoff.supplier.wrapper.InstrumentsGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.GetBondEventsRequest.EventType;
import ru.tinkoff.piapi.contract.v1.GetBondEventsResponse;
import ru.tinkoff.piapi.contract.v1.GetBondEventsResponse.BondEvent;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

public class AmortizationSyncTest extends AbstractInvestApplicationTest {
    private static final String TICKER = "RU000A10EQ34";
    private static final String UID = "ddf7148f-5261-4eb9-994f-d7afc5cf9849";
    private static final String AMORTIZING = "update bond set amortization_flag = true where ticker = 'RU000A10EQ34'";

    @Autowired
    private AmortizationSyncUseCase amortizationSyncUseCase;
    @Autowired
    private AmortizationRepository amortizationRepository;
    @Autowired
    private AuditMapper auditMapper;
    @Autowired
    private DateTimeMapper dateTimeMapper;
    @Autowired
    private InstrumentsGrpcRateLimitedWrapper instrumentsGrpcWrapper;

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql", statements = AMORTIZING)
    public void syncAllAmortizationsTest() {
        final LocalDateTime firstDate = dateTimeMapper.getNow().plusMonths(6).withNano(0);
        final LocalDateTime secondDate = dateTimeMapper.getNow().plusMonths(18).withNano(0);
        when(instrumentsGrpcWrapper.getBondEvents(any())).thenReturn(GetBondEventsResponse.newBuilder()
                .addEvents(event(firstDate, "rub", 250))
                // дата выплаты не заполнена - берётся дата события
                .addEvents(event(secondDate, "rub", 750).toBuilder().clearPayDate().setEventDate(timestamp(secondDate)))
                // размер выплаты не объявлен - такое погашение не сохраняем
                .addEvents(event(secondDate.plusMonths(1), "rub", 0))
                .build());

        amortizationSyncUseCase.syncAll(audit());

        final List<Amortization> actual = findAmortizations();
        assertThat(actual, hasSize(2));
        assertAmortization(actual.get(0), firstDate, "250");
        assertAmortization(actual.get(1), secondDate, "750");
        // запрашиваем только погашения и с явным периодом - период по умолчанию в API не описан
        verify(instrumentsGrpcWrapper).getBondEvents(argThat(request -> UID.equals(request.getInstrumentId())
                && request.getType() == EventType.EVENT_TYPE_MTY
                && request.hasFrom()
                && request.hasTo()));
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql", statements = AMORTIZING)
    public void resyncReplacesScheduleTest() {
        final LocalDateTime date = dateTimeMapper.getNow().plusMonths(6).withNano(0);
        when(instrumentsGrpcWrapper.getBondEvents(any()))
                .thenReturn(GetBondEventsResponse.newBuilder()
                        .addEvents(event(date, "rub", 250))
                        .addEvents(event(date.plusMonths(6), "rub", 750))
                        .build())
                .thenReturn(GetBondEventsResponse.newBuilder()
                        .addEvents(event(date, "rub", 1000))
                        .build());

        amortizationSyncUseCase.syncByTicker(TICKER, audit());
        amortizationSyncUseCase.syncByTicker(TICKER, audit());

        final List<Amortization> actual = findAmortizations();
        assertThat(actual, hasSize(1));
        assertAmortization(actual.getFirst(), date, "1000");
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void bondWithoutAmortizationIsSkippedTest() {
        amortizationSyncUseCase.syncAll(audit());
        amortizationSyncUseCase.syncByTicker(TICKER, audit());

        verify(instrumentsGrpcWrapper, never()).getBondEvents(any());
        assertThat(findAmortizations(), empty());
    }

    private List<Amortization> findAmortizations() {
        return amortizationRepository.findByUidIn(Set.of(UID))
                .stream()
                .sorted(Comparator.comparing(Amortization::getPaymentDate))
                .toList();
    }

    private void assertAmortization(final Amortization actual, final LocalDateTime paymentDate, final String price) {
        assertThat(actual.getPaymentDate(), equalTo(paymentDate));
        assertThat(actual.getPrice(), comparesEqualTo(new BigDecimal(price)));
        assertThat(actual.getCurrency(), equalTo("rub"));
        assertThat(actual.getTicker(), equalTo(TICKER));
        assertThat(actual.getCreated(), notNullValue());
        assertThat(actual.getCreated().getCommittedBy(), equalTo(SCHEDULER_PROCESS));
    }

    private AuditModel audit() {
        return auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS);
    }

    private static BondEvent event(final LocalDateTime payDate, final String currency, final long units) {
        return BondEvent.newBuilder()
                .setInstrumentId(UID)
                .setEventType(EventType.EVENT_TYPE_MTY)
                .setEventDate(timestamp(payDate.minusDays(1)))
                .setPayDate(timestamp(payDate))
                .setPayOneBond(MoneyValue.newBuilder().setCurrency(currency).setUnits(units))
                .build();
    }

    private static Timestamp timestamp(final LocalDateTime dateTime) {
        return Timestamp.newBuilder()
                .setSeconds(dateTime.toEpochSecond(ZoneOffset.UTC))
                .build();
    }
}
