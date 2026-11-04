package ru.invest.api.common;

import org.junit.jupiter.api.Test;
import ru.invest.api.common.model.candle.CandleInterval;
import ru.invest.api.common.model.candle.CandleModel;
import ru.invest.api.common.usecase.impl.CandleAggregationUseCaseImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.sameInstance;

public class CandleAggregationTest {
    // понедельник 28.09.2026, среда 30.09.2026, понедельник 05.10.2026
    private static final List<CandleModel> DAILY = List.of(
            candle(LocalDate.of(2026, 9, 28), "100", "101", "99", "100.5", 10),
            candle(LocalDate.of(2026, 9, 30), "100.5", "102", "100", "101", 20),
            candle(LocalDate.of(2026, 10, 5), "101", "101.5", "100.8", "101.2", 5));

    private final CandleAggregationUseCaseImpl candleAggregationUseCase = new CandleAggregationUseCaseImpl();

    @Test
    public void dayReturnsCandlesAsIsTest() {
        assertThat(candleAggregationUseCase.aggregate(DAILY, CandleInterval.DAY), sameInstance(DAILY));
    }

    @Test
    public void weekTest() {
        final List<CandleModel> weeks = candleAggregationUseCase.aggregate(DAILY, CandleInterval.WEEK);

        assertThat(weeks, hasSize(2));
        // неделя с понедельника: открытие первой дневной, закрытие последней, экстремумы и объём - по всем
        assertCandle(weeks.getFirst(), LocalDate.of(2026, 9, 28), "100", "102", "99", "101", 30);
        assertCandle(weeks.getLast(), LocalDate.of(2026, 10, 5), "101", "101.5", "100.8", "101.2", 5);
    }

    @Test
    public void monthTest() {
        final List<CandleModel> months = candleAggregationUseCase.aggregate(DAILY, CandleInterval.MONTH);

        assertThat(months, hasSize(2));
        assertCandle(months.getFirst(), LocalDate.of(2026, 9, 1), "100", "102", "99", "101", 30);
        assertCandle(months.getLast(), LocalDate.of(2026, 10, 1), "101", "101.5", "100.8", "101.2", 5);
    }

    @Test
    public void emptyTest() {
        assertThat(candleAggregationUseCase.aggregate(List.of(), CandleInterval.MONTH), empty());
    }

    private static void assertCandle(final CandleModel candle, final LocalDate date, final String open, final String high,
                                     final String low, final String close, final long volume) {
        assertThat(candle.getDate(), equalTo(date));
        assertThat(candle.getOpen(), comparesEqualTo(new BigDecimal(open)));
        assertThat(candle.getHigh(), comparesEqualTo(new BigDecimal(high)));
        assertThat(candle.getLow(), comparesEqualTo(new BigDecimal(low)));
        assertThat(candle.getClose(), comparesEqualTo(new BigDecimal(close)));
        assertThat(candle.getVolume(), equalTo(volume));
    }

    private static CandleModel candle(final LocalDate date, final String open, final String high, final String low,
                                      final String close, final long volume) {
        return new CandleModel()
                .setDate(date)
                .setOpen(new BigDecimal(open))
                .setHigh(new BigDecimal(high))
                .setLow(new BigDecimal(low))
                .setClose(new BigDecimal(close))
                .setVolume(volume);
    }
}
