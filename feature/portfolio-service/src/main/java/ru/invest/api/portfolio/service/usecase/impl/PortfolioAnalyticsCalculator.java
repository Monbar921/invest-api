package ru.invest.api.portfolio.service.usecase.impl;

import ru.invest.api.portfolio.service.model.AnalyticsPositionModel;
import ru.invest.api.portfolio.service.model.AnalyticsShareModel;
import ru.invest.api.portfolio.service.model.PortfolioAnalyticsModel;
import ru.invest.api.portfolio.service.model.RateSensitivityModel;
import ru.invest.api.portfolio.service.model.UnvaluedPositionModel;
import ru.invest.api.portfolio.service.model.WeightedValueModel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Аналитика портфеля по уже собранным позициям - без Spring и БД. Доли - от стоимости оценённых позиций в рублях,
 * округляются методом наибольшего остатка: в сумме по разрезу ровно 100 %.
 */
public final class PortfolioAnalyticsCalculator {
    public static final String FIXED = "FIXED";
    public static final String FLOATING = "FLOATING";
    /** Сдвиг доходности для чувствительности к ставке, п.п. */
    public static final BigDecimal RATE_SHIFT = BigDecimal.ONE;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal CENT = new BigDecimal("0.01");
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 2;
    private static final int DURATION_SCALE = 4;
    private static final int RAW_SCALE = 10;

    private PortfolioAnalyticsCalculator() {
    }

    public static PortfolioAnalyticsModel calculate(final List<AnalyticsPositionModel> positions) {
        final List<AnalyticsPositionModel> valued = positions.stream()
                .filter(position -> position.valueInRub() != null)
                // по убыванию стоимости - в этом порядке тикеры внутри групп
                .sorted(Comparator.comparing(AnalyticsPositionModel::valueInRub).reversed()
                        .thenComparing(AnalyticsPositionModel::ticker))
                .toList();
        final List<UnvaluedPositionModel> unvalued = positions.stream()
                .filter(position -> position.valueInRub() == null)
                .map(position -> new UnvaluedPositionModel(position.ticker(), position.bondName(),
                        position.unvaluedReason()))
                .toList();
        final BigDecimal total = valued.stream().map(AnalyticsPositionModel::valueInRub)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            final WeightedValueModel none = new WeightedValueModel(null, BigDecimal.ZERO.setScale(PERCENT_SCALE));
            return new PortfolioAnalyticsModel(null, positions.size(), unvalued, List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), none, none, none, null, null);
        }

        final List<AnalyticsShareModel> issuers = shares(valued, total,
                position -> position.issuerId() == null ? null : position.issuerId().toString(),
                AnalyticsPositionModel::issuerTitle, byValue());
        return new PortfolioAnalyticsModel(
                total.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                positions.size(),
                unvalued,
                issuers,
                shares(valued, total, position -> lower(position.sector()), none(), byValue()),
                shares(valued, total, AnalyticsPositionModel::ratingGrade, AnalyticsPositionModel::ratingGrade,
                        // от лучшей ступени к худшей
                        Comparator.comparing((Group group) -> group.score,
                                Comparator.nullsLast(Comparator.reverseOrder()))),
                shares(valued, total, position -> lower(position.currency()), none(), byValue()),
                shares(valued, total, PortfolioAnalyticsCalculator::couponType, none(), byValue()),
                shares(valued, total,
                        position -> position.maturityDate() == null
                                ? null
                                : String.valueOf(position.maturityDate().getYear()),
                        none(), Comparator.comparing((Group group) -> group.key)),
                weighted(valued, total, AnalyticsPositionModel::yieldToMaturity, PERCENT_SCALE),
                weighted(valued, total, AnalyticsPositionModel::durationMacaulay, DURATION_SCALE),
                weighted(valued, total, AnalyticsPositionModel::durationModified, DURATION_SCALE),
                issuers.stream().filter(share -> share.key() != null).findFirst().orElse(null),
                rateSensitivity(valued, total));
    }

    /**
     * Группы по признаку; группа «нет данных» (ключ null) - всегда в конце.
     *
     * @param title название группы - из первой позиции группы
     * @param order порядок групп с данными
     */
    private static List<AnalyticsShareModel> shares(final List<AnalyticsPositionModel> valued, final BigDecimal total,
                                                    final Function<AnalyticsPositionModel, String> key,
                                                    final Function<AnalyticsPositionModel, String> title,
                                                    final Comparator<Group> order) {
        final Map<String, Group> groups = new LinkedHashMap<>();
        valued.forEach(position -> {
            final String groupKey = key.apply(position);
            groups.computeIfAbsent(groupKey, ignored -> new Group(groupKey,
                            groupKey == null ? null : title.apply(position), position.ratingScore()))
                    .add(position);
        });
        final List<Group> sorted = new ArrayList<>(groups.values().stream().filter(group -> group.key != null)
                .sorted(order.thenComparing(group -> group.key))
                .toList());
        if (groups.containsKey(null)) {
            sorted.add(groups.get(null));
        }
        final List<BigDecimal> percents = largestRemainder(sorted.stream().map(group -> group.value).toList(), total);
        final List<AnalyticsShareModel> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            final Group group = sorted.get(i);
            result.add(new AnalyticsShareModel(group.key, group.title,
                    group.value.setScale(MONEY_SCALE, RoundingMode.HALF_UP), percents.get(i),
                    List.copyOf(group.tickers)));
        }
        return result;
    }

    private static Comparator<Group> byValue() {
        return Comparator.comparing((Group group) -> group.value).reversed();
    }

    private static Function<AnalyticsPositionModel, String> none() {
        return position -> null;
    }

    /**
     * Доли в % с 2 знаками, в сумме ровно 100: каждая округляется вниз, недостающие сотые получают доли с наибольшим
     * отброшенным остатком (при равенстве - большая стоимость, затем порядок в списке).
     */
    public static List<BigDecimal> largestRemainder(final List<BigDecimal> values, final BigDecimal total) {
        final List<BigDecimal> floors = new ArrayList<>();
        final List<BigDecimal> remainders = new ArrayList<>();
        for (final BigDecimal value : values) {
            final BigDecimal raw = value.multiply(HUNDRED).divide(total, RAW_SCALE, RoundingMode.HALF_UP);
            final BigDecimal floor = raw.setScale(PERCENT_SCALE, RoundingMode.DOWN);
            floors.add(floor);
            remainders.add(raw.subtract(floor));
        }
        int missing = HUNDRED.subtract(floors.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .divide(CENT, 0, RoundingMode.HALF_UP).intValueExact();
        final List<Integer> order = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing((Integer i) -> remainders.get(i)).reversed()
                .thenComparing((Integer i) -> values.get(i), Comparator.reverseOrder())
                .thenComparing(Comparator.naturalOrder()));
        for (int i = 0; missing > 0 && !order.isEmpty(); i = (i + 1) % order.size(), missing--) {
            final int index = order.get(i);
            floors.set(index, floors.get(index).add(CENT));
        }
        return floors;
    }

    private static WeightedValueModel weighted(final List<AnalyticsPositionModel> valued, final BigDecimal total,
                                               final Function<AnalyticsPositionModel, BigDecimal> metric,
                                               final int scale) {
        BigDecimal covered = BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (final AnalyticsPositionModel position : valued) {
            final BigDecimal value = metric.apply(position);
            if (value != null) {
                covered = covered.add(position.valueInRub());
                sum = sum.add(value.multiply(position.valueInRub()));
            }
        }
        return new WeightedValueModel(covered.signum() == 0 ? null : sum.divide(covered, scale, RoundingMode.HALF_UP),
                share(covered, total));
    }

    /**
     * ΔV ≈ −Dmod × Δy × V по облигациям с постоянным купоном и модифицированной дюрацией. Флоатер не учитывается:
     * его купон идёт за ставкой, и дюрация по известным купонам не говорит, как цена ответит на сдвиг ставки.
     */
    private static RateSensitivityModel rateSensitivity(final List<AnalyticsPositionModel> valued,
                                                        final BigDecimal total) {
        BigDecimal covered = BigDecimal.ZERO;
        BigDecimal weightedDuration = BigDecimal.ZERO;
        for (final AnalyticsPositionModel position : valued) {
            if (Boolean.TRUE.equals(position.fixedCoupon()) && position.durationModified() != null) {
                covered = covered.add(position.valueInRub());
                weightedDuration = weightedDuration.add(position.durationModified().multiply(position.valueInRub()));
            }
        }
        if (covered.signum() == 0) {
            return null;
        }
        // Dmod в годах = % изменения цены на 1 п.п. доходности
        final BigDecimal change = weightedDuration.multiply(RATE_SHIFT)
                .divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
        return new RateSensitivityModel(RATE_SHIFT, change.negate(), change,
                weightedDuration.multiply(RATE_SHIFT).divide(covered, PERCENT_SCALE, RoundingMode.HALF_UP),
                share(covered, total));
    }

    private static BigDecimal share(final BigDecimal part, final BigDecimal total) {
        return part.multiply(HUNDRED).divide(total, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static String couponType(final AnalyticsPositionModel position) {
        if (position.fixedCoupon() == null) {
            return null;
        }
        return position.fixedCoupon() ? FIXED : FLOATING;
    }

    private static String lower(final String value) {
        return value == null || value.isBlank() ? null : value.toLowerCase(Locale.ROOT);
    }

    /** Позиции с одним значением признака */
    private static final class Group {
        private final String key;
        private final String title;
        /** Балл рейтинга - только для порядка ступеней */
        private final Integer score;
        private final List<String> tickers = new ArrayList<>();
        private BigDecimal value = BigDecimal.ZERO;

        private Group(final String key, final String title, final Integer score) {
            this.key = key;
            this.title = title;
            this.score = score;
        }

        private void add(final AnalyticsPositionModel position) {
            value = value.add(position.valueInRub());
            tickers.add(position.ticker());
        }
    }
}
