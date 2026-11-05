package ru.invest.api.news.supplier.usecase.impl;

import org.apache.commons.lang3.StringUtils;
import ru.invest.api.common.entity.BondIssuer;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ищет в тексте новости выпуски (ISIN) и эмитентов из системы.
 * <ul>
 *     <li>Название в кавычках - «Публичное акционерное общество "Селигдар"» -> {@code "селигдар"} в кавычках, без учёта
 *     регистра: без кавычек короткие названия («Россия», «Транс») находились бы где угодно.</li>
 *     <li>Название без кавычек - «Банк ВТБ (публичное акционерное общество)» -> «Банк ВТБ» целыми словами
 *     с учётом регистра: имя собственное пишут с заглавной.</li>
 * </ul>
 */
final class IssuerMatcher {
    static final Pattern ISIN = Pattern.compile("(?<![0-9A-Z])[A-Z]{2}[0-9A-Z]{9}[0-9](?![0-9A-Z])");

    private static final char QUOTE = '"';
    private static final int MIN_QUOTED_LENGTH = 3;
    // без кавычек нужна длина побольше: «Банк ВТБ» ещё да, двухбуквенные аббревиатуры - нет
    private static final int MIN_PLAIN_LENGTH = 5;
    private static final Pattern PARENTHESES = Pattern.compile("\\([^)]*\\)");
    private static final Pattern LEGAL_FORMS = Pattern.compile(
            "(?iu)\\b(публичное|непубличное|закрытое|открытое)?\\s*акционерное\\s+общество\\b"
                    + "|(?iu)\\bобщество\\s+с\\s+ограниченной\\s+ответственностью\\b"
                    + "|(?u)\\b(ПАО|АО|ООО|ЗАО|ОАО|НАО|НКО|МФК|ПКО)\\b");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final Map<Long, String> quotedNames = new HashMap<>();
    private final Map<Long, Pattern> plainNames = new HashMap<>();
    private final Map<String, Long> emitterByIsin = new HashMap<>();

    IssuerMatcher(final Collection<BondIssuer> issuers) {
        issuers.forEach(issuer -> {
            emitterByIsin.putIfAbsent(issuer.getIsin(), issuer.getEmitterId());
            final String title = normalizeQuotes(issuer.getTitle());
            if (title == null) {
                return;
            }
            final String quoted = quotedName(title);
            if (quoted != null) {
                quotedNames.putIfAbsent(issuer.getEmitterId(), quoted.toLowerCase(Locale.ROOT));
                return;
            }
            final String plain = plainName(title);
            if (plain != null) {
                plainNames.putIfAbsent(issuer.getEmitterId(),
                        Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(plain) + "(?![\\p{L}\\d])"));
            }
        });
    }

    /**
     * ISIN в тексте - все, в том числе выпусков, которых нет в системе.
     */
    static Set<String> isins(final String text) {
        final Set<String> isins = new LinkedHashSet<>();
        final Matcher matcher = ISIN.matcher(StringUtils.defaultString(text));
        while (matcher.find()) {
            isins.add(matcher.group());
        }
        return isins;
    }

    /**
     * Эмитенты: по ISIN их выпусков и по названию.
     */
    Set<Long> emitters(final String text, final Collection<String> isins) {
        final Set<Long> emitters = isins.stream()
                .map(emitterByIsin::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        final String normalized = StringUtils.defaultString(normalizeQuotes(text));
        final String lower = normalized.toLowerCase(Locale.ROOT);
        quotedNames.forEach((emitterId, name) -> {
            if (lower.contains(QUOTE + name + QUOTE)) {
                emitters.add(emitterId);
            }
        });
        plainNames.forEach((emitterId, name) -> {
            if (name.matcher(normalized).find()) {
                emitters.add(emitterId);
            }
        });
        return emitters;
    }

    boolean isKnownIsin(final String isin) {
        return emitterByIsin.containsKey(isin);
    }

    static String normalizeQuotes(final String text) {
        return text == null ? null : StringUtils.replaceChars(text, "«»“”„", "\"\"\"\"\"");
    }

    /**
     * От первой до последней кавычки: у «ООО "Концерн "РОССИУМ"» - «Концерн "РОССИУМ».
     */
    private static String quotedName(final String title) {
        final int first = title.indexOf(QUOTE);
        final int last = title.lastIndexOf(QUOTE);
        if (first < 0 || last <= first) {
            return null;
        }
        final String name = StringUtils.trimToNull(title.substring(first + 1, last));
        return name == null || name.length() < MIN_QUOTED_LENGTH ? null : name;
    }

    /**
     * Без скобок и формы собственности; должно начинаться с заглавной: «министерство финансов области» - не имя.
     */
    private static String plainName(final String title) {
        final String withoutForms = LEGAL_FORMS.matcher(PARENTHESES.matcher(title).replaceAll(" ")).replaceAll(" ");
        final String name = StringUtils.trimToNull(SPACES.matcher(withoutForms).replaceAll(" "));
        if (name == null || name.length() < MIN_PLAIN_LENGTH || !Character.isUpperCase(name.charAt(0))) {
            return null;
        }
        return name;
    }
}
