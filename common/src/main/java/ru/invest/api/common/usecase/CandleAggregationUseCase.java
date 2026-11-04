package ru.invest.api.common.usecase;

import ru.invest.api.common.model.candle.CandleInterval;
import ru.invest.api.common.model.candle.CandleModel;

import java.util.List;

public interface CandleAggregationUseCase {
    /**
     * Собирает дневные свечи в свечи интервала: открытие - первой дневной, закрытие - последней, максимум и минимум -
     * по всем, объём - сумма. Дата свечи - первый день периода (понедельник недели, первое число месяца).
     *
     * @param dailyCandles дневные свечи по возрастанию даты
     */
    List<CandleModel> aggregate(List<CandleModel> dailyCandles, CandleInterval interval);
}
