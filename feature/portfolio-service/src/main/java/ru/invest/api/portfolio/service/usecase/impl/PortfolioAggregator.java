package ru.invest.api.portfolio.service.usecase.impl;

import ru.invest.api.portfolio.service.model.PortfolioGroupModel;
import ru.invest.api.portfolio.service.model.PortfolioTotalModel;
import ru.invest.api.portfolio.service.model.PositionModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Свод покупок портфеля: строка на облигацию и итоги по валютам. Только сложение готовых сумм покупок - цены и курсы
 * здесь не запрашиваются.
 */
final class PortfolioAggregator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENT_SCALE = 2;
    private static final int PRICE_SCALE = 4;

    private PortfolioAggregator() {
    }

    /**
     * Покупки одной облигации (по тикеру) - одна строка; строки в порядке тикеров, покупки внутри - по дате.
     */
    static List<PortfolioGroupModel> groups(final List<PositionModel> positions) {
        return positions.stream()
                .collect(Collectors.groupingBy(PositionModel::ticker, LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(PortfolioAggregator::group)
                .toList();
    }

    private static PortfolioGroupModel group(final List<PositionModel> positions) {
        final PositionModel first = positions.getFirst();
        final int quantity = positions.stream().mapToInt(PositionModel::quantity).sum();
        // у покупок одной облигации цена и номинал общие: нет цены у одной - нет у всех
        final boolean priced = positions.stream().allMatch(position -> position.revenue() != null);
        final BigDecimal buyAmount = sumOrNull(positions, PositionModel::buyAmount);
        final BigDecimal currentAmount = priced ? sumOrNull(positions, PositionModel::currentAmount) : null;
        final BigDecimal revenue = buyAmount == null || currentAmount == null ? null : currentAmount.subtract(buyAmount);
        final BigDecimal buyAmountInRub = sumOrNull(positions, PositionModel::buyAmountInRub);
        final BigDecimal currentAmountInRub = sumOrNull(positions, PositionModel::currentAmountInRub);
        return new PortfolioGroupModel(first.ticker(), first.bondName(), first.isin(), first.currency(), quantity,
                positions.stream().map(PositionModel::broker).filter(Objects::nonNull).distinct().sorted().toList(),
                positions.stream().map(PositionModel::buyDate).min(Comparator.naturalOrder()).orElse(null),
                positions.stream().map(PositionModel::buyDate).max(Comparator.naturalOrder()).orElse(null),
                first.nominal(), averageBuyPrice(positions, quantity), first.currentPrice(), buyAmount, currentAmount,
                revenue, percent(revenue, buyAmount), buyAmountInRub, currentAmountInRub,
                buyAmountInRub == null || currentAmountInRub == null
                        ? null
                        : currentAmountInRub.subtract(buyAmountInRub),
                positions.stream()
                        .sorted(Comparator.comparing(PositionModel::buyDate).thenComparing(PositionModel::id))
                        .toList());
    }

    /** Средняя цена покупки в % от номинала, взвешенная по количеству: номинал у покупок одной облигации общий */
    private static BigDecimal averageBuyPrice(final List<PositionModel> positions, final int quantity) {
        final BigDecimal weighted = positions.stream()
                .map(position -> position.buyPrice().multiply(BigDecimal.valueOf(position.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return weighted.divide(BigDecimal.valueOf(quantity), PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /** По валюте номинала, в порядке позиций; позиции без текущей цены не входят */
    static List<PortfolioTotalModel> totals(final List<PositionModel> positions) {
        final Map<String, List<PositionModel>> byCurrency = positions.stream()
                .filter(position -> position.currency() != null && position.revenue() != null)
                .collect(Collectors.groupingBy(PositionModel::currency, LinkedHashMap::new, Collectors.toList()));
        return byCurrency.entrySet().stream()
                .map(entry -> {
                    final BigDecimal buy = sumOrNull(entry.getValue(), PositionModel::buyAmount);
                    final BigDecimal current = sumOrNull(entry.getValue(), PositionModel::currentAmount);
                    final BigDecimal revenue = current.subtract(buy);
                    // рубли итога - сумма рублей покупок: у каждой своя рублёвая стоимость покупки
                    final boolean allInRub = entry.getValue().stream()
                            .allMatch(position -> position.revenueInRub() != null);
                    final BigDecimal buyInRub = allInRub ? sumOrNull(entry.getValue(), PositionModel::buyAmountInRub) : null;
                    final BigDecimal currentInRub = allInRub
                            ? sumOrNull(entry.getValue(), PositionModel::currentAmountInRub)
                            : null;
                    return new PortfolioTotalModel(entry.getKey(), buy, current, revenue, percent(revenue, buy),
                            buyInRub, currentInRub, allInRub ? currentInRub.subtract(buyInRub) : null);
                })
                .toList();
    }

    /** Доход в % к стоимости покупки; null - посчитать нельзя */
    static BigDecimal percent(final BigDecimal revenue, final BigDecimal buyAmount) {
        if (revenue == null || buyAmount == null || buyAmount.signum() == 0) {
            return null;
        }
        return revenue.multiply(HUNDRED).divide(buyAmount, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    /** Сумма поля; null, если хотя бы у одной покупки его нет - неполная сумма вводила бы в заблуждение */
    private static BigDecimal sumOrNull(final List<PositionModel> positions,
                                        final Function<PositionModel, BigDecimal> field) {
        if (positions.stream().map(field).anyMatch(Objects::isNull)) {
            return null;
        }
        return positions.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
