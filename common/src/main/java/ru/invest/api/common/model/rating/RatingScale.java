package ru.invest.api.common.model.rating;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Единая шкала национальных рейтингов АКРА и НКР: у обоих агентств одинаковые буквенные ступени, различается только
 * суффикс («AA-(RU)» и «AA-.ru»), поэтому ступень без суффикса и есть общая шкала. Балл - номер ступени снизу:
 * D - 0, AAA - 20; больше - надёжнее. RD (АКРА) и SD (НКР) - выборочный дефолт, одна ступень.
 */
public final class RatingScale {
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 20;

    private static final Map<String, Integer> SCORES = new LinkedHashMap<>();
    // от лучшей ступени к худшей - так их показывает UI в выпадающем списке
    private static final List<String> GRADES = List.of("AAA", "AA+", "AA", "AA-", "A+", "A", "A-", "BBB+", "BBB", "BBB-",
            "BB+", "BB", "BB-", "B+", "B", "B-", "CCC", "CC", "C", "SD", "D");

    static {
        int score = MAX_SCORE;
        for (final String grade : GRADES) {
            if ("SD".equals(grade)) {
                // RD и SD - одна ступень, ниже C
                score = 1;
            }
            SCORES.put(grade, score);
            score--;
        }
        SCORES.put("RD", 1);
        // у CCC агентства тоже ставят плюс и минус - на общей шкале это одна ступень
        SCORES.put("CCC+", SCORES.get("CCC"));
        SCORES.put("CCC-", SCORES.get("CCC"));
    }

    private RatingScale() {
    }

    /**
     * Балл ступени («AA-» - 17); неизвестная ступень - пусто.
     */
    public static Optional<Integer> score(final String grade) {
        return grade == null ? Optional.empty()
                : Optional.ofNullable(SCORES.get(grade.trim().toUpperCase(Locale.ROOT)));
    }

    /**
     * Ступени от лучшей к худшей - для фильтра.
     */
    public static List<String> grades() {
        return GRADES;
    }

    public static boolean isGrade(final String grade) {
        return score(grade).isPresent();
    }
}
