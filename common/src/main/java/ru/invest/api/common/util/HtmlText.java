package ru.invest.api.common.util;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * Текст из HTML внешних источников: теги и комментарии убираются, сущности (&amp;quot;, &amp;nbsp;) раскрываются.
 */
public final class HtmlText {
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00A0]+");
    private static final String ELLIPSIS = "…";

    private HtmlText() {
    }

    public static String toPlainText(final String html) {
        if (StringUtils.isBlank(html)) {
            return null;
        }

        final String withoutTags = TAG.matcher(COMMENT.matcher(html).replaceAll(" ")).replaceAll(" ");
        return StringUtils.trimToNull(SPACES.matcher(HtmlUtils.htmlUnescape(withoutTags)).replaceAll(" "));
    }

    /**
     * Начало текста не длиннее maxLength символов, обрезанное по границе слова.
     */
    public static String abbreviate(final String text, final int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        final String head = text.substring(0, maxLength);
        final int lastSpace = head.lastIndexOf(' ');
        return (lastSpace > 0 ? head.substring(0, lastSpace) : head).stripTrailing() + ELLIPSIS;
    }
}
