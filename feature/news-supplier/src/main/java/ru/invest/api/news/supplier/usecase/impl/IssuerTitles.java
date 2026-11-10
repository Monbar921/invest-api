package ru.invest.api.news.supplier.usecase.impl;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Короткое название эмитента: «Публичное акционерное общество "Селигдар"» -> «ПАО "Селигдар"».
 */
public final class IssuerTitles {
    /**
     * Полные названия форм собственности -> сокращения.
     * Длинные формы раньше коротких: «акционерное общество» входит в «публичное акционерное общество».
     */
    private static final Map<Pattern, String> LEGAL_FORMS = Map.of(
            legalForm("публичное акционерное общество"), "ПАО",
            legalForm("непубличное акционерное общество"), "АО",
            legalForm("акционерное общество"), "АО",
            legalForm("общество с ограниченной ответственностью"), "ООО");
    private static final List<Pattern> LEGAL_FORM_ORDER = LEGAL_FORMS.keySet()
            .stream()
            .sorted(Comparator.comparing((Pattern pattern) -> pattern.pattern().length()).reversed())
            .toList();

    private IssuerTitles() {
    }

    public static String shortTitle(final String title) {
        if (title == null) {
            return null;
        }
        String result = title;
        for (final Pattern form : LEGAL_FORM_ORDER) {
            result = form.matcher(result).replaceAll(LEGAL_FORMS.get(form));
        }
        return result.trim();
    }

    private static Pattern legalForm(final String words) {
        final String spaces = Arrays.stream(words.split(" ")).map(Pattern::quote).collect(Collectors.joining("\s+"));
        return Pattern.compile(spaces, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
