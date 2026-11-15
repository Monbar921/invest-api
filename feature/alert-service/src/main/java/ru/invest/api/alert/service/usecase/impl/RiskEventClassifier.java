package ru.invest.api.alert.service.usecase.impl;

import ru.invest.api.alert.service.model.RiskKind;
import ru.invest.api.common.model.news.NewsSource;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Какой риск описывает новость - только по заголовку: текст новостей не хранится. Порядок проверок важен: технический
 * дефолт раньше дефолта, понижение до D / RD / SD - дефолт, а не просто понижение рейтинга.
 */
public final class RiskEventClassifier {
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Set<NewsSource> RATING_AGENCIES = Set.of(NewsSource.ACRA, NewsSource.NKR);
    // «допустил технический дефолт», «о техническом дефолте»
    private static final Pattern TECHNICAL_DEFAULT = Pattern.compile("техническ\\p{L}*\\s+дефолт", FLAGS);
    // «допустила дефолт», «дефолта по купону»; не часть другого слова
    private static final Pattern DEFAULT = Pattern.compile("(?<!\\p{L})дефолт", FLAGS);
    // рейтинг понижен до уровня дефолта: «до RD(RU)» (АКРА), «до SD.ru», «до D.ru» (НКР)
    private static final Pattern DEFAULT_RATING = Pattern.compile(
            "(?<!\\p{L})до\\s+(?:уровня\\s+)?(?:RD|SD|D)(?:\\(RU\\)|\\.ru)", FLAGS);
    // «АКРА понизило кредитный рейтинг», «НКР понизило»
    private static final Pattern DOWNGRADE = Pattern.compile("(?<!\\p{L})понизил", FLAGS);
    // «О приостановке торгов ценными бумагами», «о приостановлении торгов»
    private static final Pattern SUSPENSION = Pattern.compile("приостанов\\p{L}*\\s+торг", FLAGS);

    private RiskEventClassifier() {
    }

    /**
     * @return риск или пусто - новость о другом
     */
    public static Optional<RiskKind> classify(final NewsSource source, final String title) {
        if (source == null || title == null || title.isBlank()) {
            return Optional.empty();
        }
        if (TECHNICAL_DEFAULT.matcher(title).find()) {
            return Optional.of(RiskKind.TECHNICAL_DEFAULT);
        }
        final boolean agency = RATING_AGENCIES.contains(source);
        if (DEFAULT.matcher(title).find() || agency && DEFAULT_RATING.matcher(title).find()) {
            return Optional.of(RiskKind.DEFAULT);
        }
        if (agency && DOWNGRADE.matcher(title).find()) {
            return Optional.of(RiskKind.RATING_DOWNGRADE);
        }
        if (source == NewsSource.MOEX && SUSPENSION.matcher(title).find()) {
            return Optional.of(RiskKind.TRADING_SUSPENSION);
        }
        return Optional.empty();
    }
}
