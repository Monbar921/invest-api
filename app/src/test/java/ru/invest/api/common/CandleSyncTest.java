package ru.invest.api.common;

import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import ru.invest.api.AbstractInvestApplicationTest;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.candle.CandleModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.usecase.CandleSyncUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffCandleUseCase;
import ru.invest.api.tinkoff.supplier.wrapper.MarketDataGrpcRateLimitedWrapper;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.GetCandlesRequest;
import ru.tinkoff.piapi.contract.v1.GetCandlesResponse;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.Quotation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.invest.api.common.constants.SchedulerConstants.SCHEDULER_PROCESS;

public class CandleSyncTest extends AbstractInvestApplicationTest {
    private static final String TICKER = "RU000A10EQ34";
    private static final LocalDate HISTORY_START = LocalDate.of(1990, 1, 1);

    @Autowired
    private CandleSyncUseCase candleSyncUseCase;
    @Autowired
    private TinkoffCandleUseCase tinkoffCandleUseCase;
    @Autowired
    private BondRepository bondRepository;
    @Autowired
    private AuditMapper auditMapper;
    @Autowired
    private DateTimeMapper dateTimeMapper;
    @Autowired
    private MarketDataGrpcRateLimitedWrapper marketDataGrpcWrapper;

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void firstSyncLoadsWholeHistoryTest() {
        final LocalDate today = today();
        // свечи есть только в последнем окне - следующее, более раннее окно пустое и поиск истории останавливается
        when(marketDataGrpcWrapper.getCandles(any())).thenAnswer(invocation -> {
            final GetCandlesRequest request = invocation.getArgument(0);
            return toDate(request.getTo()).isAfter(today)
                    ? response(candle(today.minusDays(2), "99.5"), candle(today.minusDays(1), "100"), candle(today, "100.4"))
                    : GetCandlesResponse.getDefaultInstance();
        });

        candleSyncUseCase.syncByTicker(TICKER, audit());

        final List<CandleModel> saved = candles();
        assertThat(saved, hasSize(3));
        assertThat(saved.getLast().getDate(), equalTo(today));
        assertThat(saved.getLast().getClose(), comparesEqualTo(new BigDecimal("100.4")));
        // только дневные биржевые свечи; окно с данными и пустое окно перед ним
        verify(marketDataGrpcWrapper, times(2)).getCandles(argThat(request ->
                request.getInterval() == CandleInterval.CANDLE_INTERVAL_DAY
                        && request.getCandleSourceType() == GetCandlesRequest.CandleSource.CANDLE_SOURCE_EXCHANGE));
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons-RU000A10EQ34.sql")
    public void nextSyncContinuesFromLastCandleTest() {
        final LocalDate today = today();
        when(marketDataGrpcWrapper.getCandles(any()))
                .thenReturn(response(candle(today.minusDays(1), "100"), candle(today, "100.4")))
                .thenReturn(GetCandlesResponse.getDefaultInstance())
                // повторная синхронизация - с последней свечи: она могла быть незавершённой и закрылась по-другому
                .thenReturn(response(candle(today, "100.9")));

        candleSyncUseCase.syncByTicker(TICKER, audit());
        candleSyncUseCase.syncByTicker(TICKER, audit());

        final List<CandleModel> saved = candles();
        assertThat(saved, hasSize(2));
        assertThat(saved.getLast().getClose(), comparesEqualTo(new BigDecimal("100.9")));
        verify(marketDataGrpcWrapper).getCandles(argThat(request -> toDate(request.getFrom()).equals(today)));
    }

    @Test
    @Sql(scripts = "/sql/bonds-and-coupons.sql")
    public void errorForOneBondDoesNotStopSyncTest() {
        final List<Bond> bonds = bondRepository.findAll();
        final String failingUid = bonds.getFirst().getUid();
        when(marketDataGrpcWrapper.getCandles(any())).thenAnswer(invocation -> {
            final GetCandlesRequest request = invocation.getArgument(0);
            if (failingUid.equals(request.getInstrumentId())) {
                throw new IllegalStateException("tinkoff is unavailable");
            }
            return toDate(request.getTo()).isAfter(today()) ? response(candle(today(), "100")) : GetCandlesResponse.getDefaultInstance();
        });

        candleSyncUseCase.syncAll(audit());

        for (final Bond bond : bonds) {
            final List<CandleModel> saved = tinkoffCandleUseCase.find(bond.getUid(), HISTORY_START, today());
            if (failingUid.equals(bond.getUid())) {
                assertThat(bond.getTicker(), saved, empty());
            } else {
                assertThat(bond.getTicker(), saved, not(empty()));
            }
        }
    }

    private List<CandleModel> candles() {
        final Bond bond = bondRepository.findByTicker(TICKER).orElseThrow();
        return tinkoffCandleUseCase.find(bond.getUid(), HISTORY_START, today());
    }

    private LocalDate today() {
        return dateTimeMapper.getNow().toLocalDate();
    }

    private AuditModel audit() {
        return auditMapper.toCurrentAuditModel(SCHEDULER_PROCESS);
    }

    private static GetCandlesResponse response(final HistoricCandle... candles) {
        return GetCandlesResponse.newBuilder().addAllCandles(List.of(candles)).build();
    }

    private static HistoricCandle candle(final LocalDate date, final String close) {
        final Quotation price = quotation(new BigDecimal(close));
        return HistoricCandle.newBuilder()
                // дневная свеча MOEX начинается в 07:00 UTC
                .setTime(Timestamp.newBuilder().setSeconds(date.atTime(7, 0).toEpochSecond(ZoneOffset.UTC)))
                .setOpen(price)
                .setHigh(price)
                .setLow(price)
                .setClose(price)
                .setVolume(10)
                .setIsComplete(true)
                .build();
    }

    private static Quotation quotation(final BigDecimal value) {
        return Quotation.newBuilder()
                .setUnits(value.longValue())
                .setNano(value.remainder(BigDecimal.ONE).movePointRight(9).intValueExact())
                .build();
    }

    private static LocalDate toDate(final Timestamp timestamp) {
        return LocalDate.ofInstant(java.time.Instant.ofEpochSecond(timestamp.getSeconds()), ZoneOffset.UTC);
    }
}
