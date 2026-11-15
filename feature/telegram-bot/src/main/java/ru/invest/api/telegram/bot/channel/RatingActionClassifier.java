package ru.invest.api.telegram.bot.channel;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.model.rating.RatingAgency;
import ru.invest.api.news.supplier.usecase.impl.CreditRatingParser;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Какое рейтинговое действие в заголовке релиза АКРА или НКР годится для канала: присвоение, повышение, понижение,
 * отзыв. Подтверждение рейтинга, изменение прогноза и пересмотр - нет: их десятки в день, и рейтинг не меняется.
 * Рейтинг - только по национальной шкале (как в скринере): заголовок без него, кроме отзыва, - не пост.
 */
public final class RatingActionClassifier {
    // глагол действия; «повысило прогноз» - изменение прогноза, а не рейтинга: следующее слово проверяется отдельно
    private static final Pattern VERB = Pattern.compile(
            "(?<![\\p{L}])(присвои|повыси|пониз|отозва)\\p{L}*(\\s+\\p{L}+)?", Pattern.UNICODE_CASE);
    private static final String OUTLOOK = "прогноз";

    private RatingActionClassifier() {
    }

    /**
     * @return вид действия; пусто - не пост (не рейтинговое агентство, подтверждение, прогноз, нет национальной шкалы)
     */
    public static Optional<Change> classify(final NewsSource source, final String title) {
        final RatingAgency agency = switch (source) {
            case ACRA -> RatingAgency.ACRA;
            case NKR -> RatingAgency.NKR;
            default -> null;
        };
        if (agency == null || title == null || CreditRatingParser.parse(agency, title).isEmpty()) {
            return Optional.empty();
        }
        final Matcher matcher = VERB.matcher(title.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            final String next = matcher.group(2) == null ? "" : matcher.group(2).strip();
            if (next.startsWith(OUTLOOK)) {
                continue;
            }
            return Optional.of(switch (matcher.group(1)) {
                case "присвои" -> Change.ASSIGNED;
                case "повыси" -> Change.UPGRADED;
                case "пониз" -> Change.DOWNGRADED;
                default -> Change.WITHDRAWN;
            });
        }
        return Optional.empty();
    }

    @Getter
    @RequiredArgsConstructor
    public enum Change {
        ASSIGNED("присвоение"),
        UPGRADED("повышение"),
        DOWNGRADED("понижение"),
        WITHDRAWN("отзыв");

        private final String title;
    }
}
