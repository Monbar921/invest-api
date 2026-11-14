package ru.invest.api.news.supplier.usecase.impl;

import org.apache.commons.lang3.StringUtils;
import ru.invest.api.common.model.rating.RatingAgency;
import ru.invest.api.common.model.rating.RatingOutlook;
import ru.invest.api.common.model.rating.RatingScale;
import ru.invest.api.common.model.rating.RatingScope;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбирает заголовок релиза АКРА или НКР в рейтинговые действия. Берутся только рейтинги по национальной шкале
 * («AA-(RU)», «AA-.ru»): международная шкала другого агентства несравнима, ожидаемый рейтинг («eAA-(RU)») - ещё не
 * рейтинг. Заголовок - единственный источник: тексты релизов лежат в PDF.
 * <ul>
 *     <li>Рейтинг выпуска - в заголовке есть «выпуск»: рейтинг - последний в заголовке, выпуски - по ISIN, а без
 *     ISIN (НКР) - по номерам серий после «серии».</li>
 *     <li>Иначе - рейтинг эмитента: стоящий после «до» («понизило ... с A.ru до BBB+.ru»), без «до» - первый.
 *     ISIN в таком заголовке («... и его облигаций (RU000A105NB4) — на уровне AA+(RU)») - рейтинг выпуска, первый
 *     после ISIN.</li>
 *     <li>«Отозвало» - рейтинг отозван.</li>
 * </ul>
 */
public final class CreditRatingParser {
    private static final String GRADES = "(AAA|AA|A|BBB|BB|B|CCC|CC|C|RD|SD|D)([+-]?)";
    // буква перед ступенью - не рейтинг: «eAAA(RU)» - ожидаемый, «AAA» внутри слова - не ступень
    private static final Pattern ACRA_TOKEN = Pattern.compile("(?<![\\p{L}\\d])" + GRADES + "\\s?\\(RU\\)");
    private static final Pattern NKR_TOKEN = Pattern.compile("(?<![\\p{L}\\d])" + GRADES + "\\.RU(?![\\p{L}\\d])");
    private static final Pattern BEFORE_NEW_RATING = Pattern.compile("(?u)(^|\\s)до\\s+(уровня\\s+)?$");
    private static final Pattern SERIES = Pattern.compile("(?<![\\p{L}\\d])[\\p{L}\\d]+(?:-[\\p{L}\\d]+)+");
    private static final int LOOKBEHIND = 15;
    // кириллица, похожая на латиницу: АКРА пишет «AА+(RU)» и «ААА(RU)» с русскими буквами, а серии - «003Р-24»
    private static final Map<Character, Character> LOOKALIKES = Map.ofEntries(
            Map.entry('А', 'A'), Map.entry('В', 'B'), Map.entry('Е', 'E'), Map.entry('К', 'K'), Map.entry('М', 'M'),
            Map.entry('Н', 'H'), Map.entry('О', 'O'), Map.entry('Р', 'P'), Map.entry('С', 'C'), Map.entry('Т', 'T'),
            Map.entry('У', 'Y'), Map.entry('Х', 'X'));

    private CreditRatingParser() {
    }

    /**
     * Рейтинговые действия релиза; заголовок без рейтинга по национальной шкале и без отзыва - пусто.
     */
    public static List<Action> parse(final RatingAgency agency, final String title) {
        if (StringUtils.isBlank(title)) {
            return List.of();
        }
        final String lower = title.toLowerCase(Locale.ROOT);
        final String normalized = normalize(title);
        final List<Token> tokens = tokens(agency, normalized);
        final boolean withdrawn = lower.contains("отозвал");
        if (tokens.isEmpty() && !withdrawn) {
            return List.of();
        }

        final List<Action> actions = new ArrayList<>();
        final Matcher isins = IssuerMatcher.ISIN.matcher(title);
        if (lower.contains("выпуск")) {
            final Rating rating = withdrawn ? null : tokens.getLast().rating();
            final Set<String> found = new LinkedHashSet<>();
            while (isins.find()) {
                found.add(isins.group());
            }
            found.forEach(isin -> actions.add(new Action(RatingScope.ISSUE, isin, null, rating, null)));
            if (found.isEmpty()) {
                series(lower, normalized).forEach(series -> actions.add(new Action(RatingScope.ISSUE, null, series, rating, null)));
            }
            return actions;
        }

        final Rating issuerRating = withdrawn ? null : issuerToken(tokens, lower).rating();
        actions.add(new Action(RatingScope.ISSUER, null, null, issuerRating, withdrawn ? null : outlook(lower)));
        while (isins.find()) {
            final int position = isins.end();
            final Optional<Token> after = tokens.stream().filter(token -> token.position() > position).findFirst();
            if (withdrawn || after.isPresent()) {
                actions.add(new Action(RatingScope.ISSUE, isins.group(), null,
                        withdrawn ? null : after.get().rating(), null));
            }
        }
        return actions;
    }

    /**
     * Верхний регистр и латиница вместо похожей кириллицы - символ в символ, позиции совпадают с заголовком.
     */
    public static String normalize(final String text) {
        final String upper = text.toUpperCase(Locale.ROOT);
        final StringBuilder result = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            result.append(LOOKALIKES.getOrDefault(upper.charAt(i), upper.charAt(i)));
        }
        return result.toString();
    }

    private static List<Token> tokens(final RatingAgency agency, final String normalized) {
        final Matcher matcher = (agency == RatingAgency.ACRA ? ACRA_TOKEN : NKR_TOKEN).matcher(normalized);
        final List<Token> tokens = new ArrayList<>();
        while (matcher.find()) {
            final String grade = matcher.group(1) + matcher.group(2);
            final Optional<Integer> score = RatingScale.score(grade);
            if (score.isPresent()) {
                final String rating = agency == RatingAgency.ACRA ? grade + "(RU)" : grade + ".ru";
                tokens.add(new Token(matcher.start(), new Rating(rating, "RD".equals(grade) ? "SD" : grade, score.get())));
            }
        }
        return tokens;
    }

    private static Token issuerToken(final List<Token> tokens, final String lower) {
        return tokens.stream()
                .filter(token -> BEFORE_NEW_RATING.matcher(
                        lower.substring(Math.max(0, token.position() - LOOKBEHIND), token.position())).find())
                .findFirst()
                .orElse(tokens.getFirst());
    }

    /**
     * Номера серий после слова «серии» / «серий»: «002P-08», «БО-П10», «001P-06 и 001P-08».
     */
    private static List<String> series(final String lower, final String normalized) {
        final int start = lower.indexOf("сери");
        if (start < 0) {
            return List.of();
        }
        final Matcher matcher = SERIES.matcher(normalized.substring(start));
        final List<String> series = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.group().chars().anyMatch(Character::isDigit)) {
                series.add(matcher.group());
            }
        }
        return series;
    }

    private static RatingOutlook outlook(final String lower) {
        if (lower.contains("под наблюдени") || lower.contains("на пересмотре")) {
            return RatingOutlook.UNDER_REVIEW;
        }
        if (lower.contains("развивающ")) {
            return RatingOutlook.DEVELOPING;
        }
        if (lower.contains("негативн")) {
            return RatingOutlook.NEGATIVE;
        }
        if (lower.contains("позитивн")) {
            return RatingOutlook.POSITIVE;
        }
        return lower.contains("стабильн") ? RatingOutlook.STABLE : null;
    }

    /**
     * Рейтинг по национальной шкале: как в релизе, ступень общей шкалы и её балл.
     */
    public record Rating(String rating, String grade, int score) {
    }

    /**
     * Действие: кому (эмитент релиза, ISIN или номер серии выпуска) и какой рейтинг; rating null - отозван.
     */
    public record Action(RatingScope scope, String isin, String series, Rating rating, RatingOutlook outlook) {
    }

    private record Token(int position, Rating rating) {
    }
}
