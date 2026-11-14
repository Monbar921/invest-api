package ru.invest.api.alert.service.usecase.impl;

import ru.invest.api.common.model.parameters.BondParametersRequestModel;
import ru.invest.api.common.model.parameters.ValueRangeModel;

import java.math.BigDecimal;

/**
 * Запас у границ фильтра (тот же приём, что у порогов цены): облигация, уже попавшая в выборку, остаётся в ней, пока
 * проходит фильтр с расширенными диапазонами. Колебание цены или доходности около границы не даёт выхода и
 * повторного входа - и потока одинаковых уведомлений.
 * <p>
 * Расширяются только диапазоны значений, которые меняются вместе с ценой и данными скринера: цена (в % - на 0,2 п.п.,
 * в рублях - на 0,2%), доходность и спред к ОФЗ (0,1 п.п.), дюрация (0,05 года), оборот (10%). Списки, флаги,
 * рейтинг, даты и срок до погашения - как есть.
 */
final class FilterMargins {
    static final BigDecimal PRICE_PERCENT = new BigDecimal("0.2");
    static final BigDecimal PRICE_RELATIVE = new BigDecimal("0.002");
    static final BigDecimal YIELD = new BigDecimal("0.1");
    static final BigDecimal DURATION = new BigDecimal("0.05");
    static final BigDecimal TURNOVER_RELATIVE = new BigDecimal("0.1");

    private FilterMargins() {
    }

    /** Меняет и возвращает те же параметры */
    static BondParametersRequestModel loosen(final BondParametersRequestModel parameters) {
        parameters.setPercentagePrice(absolute(parameters.getPercentagePrice(), PRICE_PERCENT));
        parameters.setCurrentPrice(relative(parameters.getCurrentPrice(), PRICE_RELATIVE));
        parameters.setYieldToMaturity(absolute(parameters.getYieldToMaturity(), YIELD));
        parameters.setSpreadToOfz(absolute(parameters.getSpreadToOfz(), YIELD));
        parameters.setDuration(absolute(parameters.getDuration(), DURATION));
        parameters.setAvgDailyTurnover(relative(parameters.getAvgDailyTurnover(), TURNOVER_RELATIVE));
        return parameters;
    }

    private static ValueRangeModel absolute(final ValueRangeModel range, final BigDecimal margin) {
        if (range == null) {
            return null;
        }
        return new ValueRangeModel()
                .setMin(range.getMin() == null ? null : range.getMin().subtract(margin))
                .setMax(range.getMax() == null ? null : range.getMax().add(margin));
    }

    /** Граница отодвигается на долю своего модуля: у отрицательной границы - тоже наружу */
    private static ValueRangeModel relative(final ValueRangeModel range, final BigDecimal share) {
        if (range == null) {
            return null;
        }
        return new ValueRangeModel()
                .setMin(range.getMin() == null ? null : range.getMin().subtract(range.getMin().abs().multiply(share)))
                .setMax(range.getMax() == null ? null : range.getMax().add(range.getMax().abs().multiply(share)));
    }
}
