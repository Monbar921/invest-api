package ru.invest.api.feature.portfolio;

import org.junit.jupiter.api.Test;
import ru.invest.api.portfolio.service.model.AnalyticsPositionModel;
import ru.invest.api.portfolio.service.model.AnalyticsShareModel;
import ru.invest.api.portfolio.service.model.PortfolioAnalyticsModel;
import ru.invest.api.portfolio.service.model.UnvaluedPositionModel;
import ru.invest.api.portfolio.service.model.UnvaluedReason;
import ru.invest.api.portfolio.service.usecase.impl.PortfolioAnalyticsCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * Аналитика портфеля без Spring и БД: доли, «нет данных», средневзвешенные величины, чувствительность к ставке.
 */
public class PortfolioAnalyticsCalculatorTest {

    @Test
    void sharesAlwaysSumToHundred() {
        // три равные части: 33,333...% - округление вниз дало бы 99,99
        assertThat(PortfolioAnalyticsCalculator.largestRemainder(values("1", "1", "1"), new BigDecimal("3")),
                contains(comparesEqualTo(new BigDecimal("33.34")), comparesEqualTo(new BigDecimal("33.33")),
                        comparesEqualTo(new BigDecimal("33.33"))));
        // семь равных частей по 14,2857...%: вниз - 14,28 × 7 = 99,96, четыре доли получают по сотой
        final List<BigDecimal> sevenths = PortfolioAnalyticsCalculator.largestRemainder(
                values("1", "1", "1", "1", "1", "1", "1"), new BigDecimal("7"));
        assertThat(sum(sevenths), comparesEqualTo(new BigDecimal("100")));
        // наибольший остаток получает сотую: 2/3 = 66,666..., 1/3 = 33,333...
        assertThat(PortfolioAnalyticsCalculator.largestRemainder(values("2", "1"), new BigDecimal("3")),
                contains(comparesEqualTo(new BigDecimal("66.67")), comparesEqualTo(new BigDecimal("33.33"))));
        // доли «некрасивых» сумм
        final List<BigDecimal> uneven = PortfolioAnalyticsCalculator.largestRemainder(
                values("1220", "1005", "950.50"), new BigDecimal("3175.50"));
        assertThat(uneven, contains(comparesEqualTo(new BigDecimal("38.42")), comparesEqualTo(new BigDecimal("31.65")),
                comparesEqualTo(new BigDecimal("29.93"))));
    }

    @Test
    void groupsWithoutDataAndUnvaluedPositionsDoNotDisappear() {
        final PortfolioAnalyticsModel analytics = PortfolioAnalyticsCalculator.calculate(List.of(
                position("A", "100000", 1L, "ПАО \"Альфа\"", "financial", "AA", 18, "rub", true,
                        LocalDate.of(2030, 6, 1), "15", "3.5", "2"),
                position("B", "50000", 1L, "ПАО \"Альфа\"", "Financial", "A+", 16, "rub", true,
                        LocalDate.of(2028, 1, 10), "18", "4.4", "4"),
                // флоатер без эмитента, рейтинга, сектора, доходности и даты погашения
                position("C", "50000", null, null, null, null, null, "rub", false, null, null, null, "0.2"),
                new AnalyticsPositionModel("D", "Без цены", null, UnvaluedReason.NO_PRICE, 2L, "ООО \"Дельта\"",
                        "energy", "BBB", 12, "rub", true, LocalDate.of(2027, 1, 1), new BigDecimal("25"),
                        BigDecimal.ONE, new BigDecimal("0.9"))));

        assertThat(analytics.valueInRub(), comparesEqualTo(new BigDecimal("200000")));
        assertThat(analytics.bonds(), equalTo(4));
        // без оценки - отдельно, в доли не входит
        assertThat(analytics.unvalued(), contains(new UnvaluedPositionModel("D", "Без цены", UnvaluedReason.NO_PRICE)));

        assertThat(keys(analytics.issuers()), contains("1", null));
        assertThat(analytics.issuers().getFirst().title(), equalTo("ПАО \"Альфа\""));
        assertThat(analytics.issuers().getFirst().tickers(), contains("A", "B"));
        assertShares(analytics.issuers(), "75.00", "25.00");
        assertThat(analytics.largestIssuer().key(), equalTo("1"));
        assertThat(analytics.largestIssuer().share(), comparesEqualTo(new BigDecimal("75")));

        // сектор без учёта регистра
        assertThat(keys(analytics.sectors()), contains("financial", null));
        // рейтинг - от лучшей ступени к худшей, а не по доле
        assertThat(keys(analytics.ratings()), contains("AA", "A+", null));
        assertShares(analytics.ratings(), "50.00", "25.00", "25.00");
        assertThat(keys(analytics.currencies()), contains("rub"));
        assertShares(analytics.currencies(), "100.00");
        assertThat(keys(analytics.couponTypes()), contains(PortfolioAnalyticsCalculator.FIXED,
                PortfolioAnalyticsCalculator.FLOATING));
        // лестница погашений - по году
        assertThat(keys(analytics.maturities()), contains("2028", "2030", null));
        assertShares(analytics.maturities(), "25.00", "50.00", "25.00");
        for (final List<AnalyticsShareModel> shares : List.of(analytics.issuers(), analytics.sectors(),
                analytics.ratings(), analytics.currencies(), analytics.couponTypes(), analytics.maturities())) {
            assertThat(sum(shares.stream().map(AnalyticsShareModel::share).toList()),
                    comparesEqualTo(new BigDecimal("100")));
        }
    }

    @Test
    void weightedValuesAndRateSensitivity() {
        final PortfolioAnalyticsModel analytics = PortfolioAnalyticsCalculator.calculate(List.of(
                position("A", "100000", 1L, "А", "financial", "AA", 18, "rub", true, LocalDate.of(2030, 6, 1),
                        "15", "3.5", "2"),
                position("B", "50000", 2L, "Б", "financial", "A+", 16, "rub", true, LocalDate.of(2028, 1, 10),
                        "18", "4.4", "4"),
                position("C", "50000", null, null, null, null, null, "rub", false, null, null, null, "0.2")));

        // (15 × 100 000 + 18 × 50 000) / 150 000; у C доходности нет - 75 % стоимости
        assertThat(analytics.yieldToMaturity().value(), comparesEqualTo(new BigDecimal("16.00")));
        assertThat(analytics.yieldToMaturity().coveredShare(), comparesEqualTo(new BigDecimal("75.00")));
        // (3,5 × 100 000 + 4,4 × 50 000) / 150 000 = 3,8
        assertThat(analytics.durationMacaulay().value(), comparesEqualTo(new BigDecimal("3.8")));
        // у всех: (2 × 100 000 + 4 × 50 000 + 0,2 × 50 000) / 200 000
        assertThat(analytics.durationModified().value(), comparesEqualTo(new BigDecimal("2.05")));
        assertThat(analytics.durationModified().coveredShare(), comparesEqualTo(new BigDecimal("100")));

        // флоатер не учитывается: (2 × 100 000 + 4 × 50 000) × 1 % = 4 000 ₽ на 150 000 ₽
        assertThat(analytics.rateSensitivity().shiftPercent(), comparesEqualTo(BigDecimal.ONE));
        assertThat(analytics.rateSensitivity().valueChangeUp(), comparesEqualTo(new BigDecimal("-4000")));
        assertThat(analytics.rateSensitivity().valueChangeDown(), comparesEqualTo(new BigDecimal("4000")));
        assertThat(analytics.rateSensitivity().changePercent(), comparesEqualTo(new BigDecimal("2.67")));
        assertThat(analytics.rateSensitivity().coveredShare(), comparesEqualTo(new BigDecimal("75")));
    }

    @Test
    void emptyAndUnvaluedOnlyPortfolio() {
        final PortfolioAnalyticsModel empty = PortfolioAnalyticsCalculator.calculate(List.of());
        assertThat(empty.valueInRub(), nullValue());
        assertThat(empty.issuers(), empty());
        assertThat(empty.yieldToMaturity().value(), nullValue());
        assertThat(empty.largestIssuer(), nullValue());
        assertThat(empty.rateSensitivity(), nullValue());

        final PortfolioAnalyticsModel unknown = PortfolioAnalyticsCalculator.calculate(List.of(
                new AnalyticsPositionModel("X", null, null, UnvaluedReason.UNKNOWN_BOND, null, null, null, null, null,
                        null, null, null, null, null, null)));
        assertThat(unknown.bonds(), equalTo(1));
        assertThat(unknown.valueInRub(), nullValue());
        assertThat(unknown.unvalued(), contains(new UnvaluedPositionModel("X", null, UnvaluedReason.UNKNOWN_BOND)));
        assertThat(unknown.sectors(), empty());
        // без облигаций с постоянным купоном и дюрацией чувствительности нет
        assertThat(PortfolioAnalyticsCalculator.calculate(List.of(
                        position("C", "1000", null, null, null, null, null, "rub", false, null, null, null, "0.2")))
                .rateSensitivity(), nullValue());
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private static AnalyticsPositionModel position(final String ticker, final String value, final Long issuerId,
                                                   final String issuerTitle, final String sector, final String grade,
                                                   final Integer score, final String currency, final Boolean fixed,
                                                   final LocalDate maturity, final String yield, final String macaulay,
                                                   final String modified) {
        final Function<String, BigDecimal> number = text -> text == null ? null : new BigDecimal(text);
        return new AnalyticsPositionModel(ticker, ticker, number.apply(value), null, issuerId, issuerTitle, sector,
                grade, score, currency, fixed, maturity, number.apply(yield), number.apply(macaulay),
                number.apply(modified));
    }

    private static void assertShares(final List<AnalyticsShareModel> shares, final String... expected) {
        assertThat(shares.stream().map(AnalyticsShareModel::share).map(BigDecimal::toPlainString).toList(),
                equalTo(Arrays.asList(expected)));
    }

    private static List<String> keys(final List<AnalyticsShareModel> shares) {
        return shares.stream().map(AnalyticsShareModel::key).toList();
    }

    private static List<BigDecimal> values(final String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }

    private static BigDecimal sum(final List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
