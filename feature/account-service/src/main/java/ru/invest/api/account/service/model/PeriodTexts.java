package ru.invest.api.account.service.model;

import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Срок тарифа по-русски: P1M - «1 месяц», P1Y - «1 год», P1Y6M - «1 год 6 месяцев».
 */
public final class PeriodTexts {
    private static final int TEN = 10;
    private static final int HUNDRED = 100;
    private static final int FEW_FROM = 2;
    private static final int FEW_TO = 4;
    private static final int TEENS_FROM = 11;
    private static final int TEENS_TO = 14;

    private PeriodTexts() {
    }

    public static String format(final Period period) {
        if (period == null) {
            return null;
        }
        final List<String> parts = new ArrayList<>();
        if (period.getYears() > 0) {
            parts.add(plural(period.getYears(), "год", "года", "лет"));
        }
        if (period.getMonths() > 0) {
            parts.add(plural(period.getMonths(), "месяц", "месяца", "месяцев"));
        }
        if (period.getDays() > 0) {
            parts.add(plural(period.getDays(), "день", "дня", "дней"));
        }
        return String.join(" ", parts);
    }

    private static String plural(final int count, final String one, final String few, final String many) {
        final int lastTwo = count % HUNDRED;
        final int last = count % TEN;
        final String word;
        if (lastTwo >= TEENS_FROM && lastTwo <= TEENS_TO) {
            word = many;
        } else if (last == 1) {
            word = one;
        } else if (last >= FEW_FROM && last <= FEW_TO) {
            word = few;
        } else {
            word = many;
        }
        return count + " " + word;
    }
}
