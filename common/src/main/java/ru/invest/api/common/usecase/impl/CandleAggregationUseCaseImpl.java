package ru.invest.api.common.usecase.impl;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.candle.CandleInterval;
import ru.invest.api.common.model.candle.CandleModel;
import ru.invest.api.common.usecase.CandleAggregationUseCase;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CandleAggregationUseCaseImpl implements CandleAggregationUseCase {

    @Override
    public List<CandleModel> aggregate(final List<CandleModel> dailyCandles, final CandleInterval interval) {
        if (CollectionUtils.isEmpty(dailyCandles)) {
            return List.of();
        }
        if (interval == null || interval == CandleInterval.DAY) {
            return dailyCandles;
        }

        // порядок вставки сохраняет порядок дат: на вход свечи приходят по возрастанию
        final Map<LocalDate, List<CandleModel>> byPeriod = new LinkedHashMap<>();
        dailyCandles.forEach(candle -> byPeriod
                .computeIfAbsent(periodStart(candle.getDate(), interval), start -> new ArrayList<>())
                .add(candle));

        return byPeriod.entrySet()
                .stream()
                .map(entry -> merge(entry.getKey(), entry.getValue()))
                .toList();
    }

    private LocalDate periodStart(final LocalDate date, final CandleInterval interval) {
        return interval == CandleInterval.WEEK
                ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : date.withDayOfMonth(1);
    }

    private CandleModel merge(final LocalDate periodStart, final List<CandleModel> candles) {
        return new CandleModel()
                .setDate(periodStart)
                .setOpen(candles.getFirst().getOpen())
                .setClose(candles.getLast().getClose())
                .setHigh(candles.stream().map(CandleModel::getHigh).reduce(candles.getFirst().getHigh(), (a, b) -> a.max(b)))
                .setLow(candles.stream().map(CandleModel::getLow).reduce(candles.getFirst().getLow(), (a, b) -> a.min(b)))
                .setVolume(candles.stream().mapToLong(CandleModel::getVolume).sum());
    }
}
