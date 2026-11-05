package ru.invest.api.common.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Разбор RSS 2.0 и XML внешних источников. XML приходит байтами: кодировку и BOM разбирает парсер.
 */
@Slf4j
public final class RssReader {
    // анонс - начало описания; полный текст не перепечатываем, по ссылке он есть у источника
    public static final int SUMMARY_LENGTH = 300;
    /** Длина news.external_id; у СМИ id новости - её ссылка, бывает длинной */
    public static final int EXTERNAL_ID_LENGTH = 200;

    private RssReader() {
    }

    /**
     * Записи ленты; запись без id, заголовка или даты пропускается. guid нет - id новости её ссылка.
     */
    public static List<NewsModel> read(final NewsSource source, final byte[] rss) {
        final NodeList items = parse(rss).getElementsByTagName("item");
        return IntStream.range(0, items.getLength())
                .mapToObj(index -> toNews(source, (Element) items.item(index)))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * XML без DTD и внешних сущностей (XXE): это ответ внешнего сервиса.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static Document parse(final byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.EXTERNAL_SERVICE_ERROR, "Empty XML response");
        }
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (final Exception e) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Wrong XML: " + e.getMessage());
        }
    }

    public static String text(final Element element, final String tag) {
        final NodeList nodes = element.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : StringUtils.trimToNull(nodes.item(0).getTextContent());
    }

    private static Optional<NewsModel> toNews(final NewsSource source, final Element item) {
        final String link = text(item, "link");
        final String guid = StringUtils.defaultIfBlank(text(item, "guid"), link);
        final String title = HtmlText.toPlainText(text(item, "title"));
        final String published = text(item, "pubDate");
        if (StringUtils.isAnyBlank(guid, title, published)) {
            return Optional.empty();
        }

        final String description = HtmlText.toPlainText(text(item, "description"));
        try {
            return Optional.of(new NewsModel()
                    .setSource(source)
                    .setExternalId(StringUtils.left(guid, EXTERNAL_ID_LENGTH))
                    .setTitle(title)
                    .setSummary(HtmlText.abbreviate(description, SUMMARY_LENGTH))
                    .setUrl(StringUtils.trimToNull(link))
                    .setPublishedAt(OffsetDateTime.parse(published, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .withOffsetSameInstant(ZoneOffset.UTC)
                            .toLocalDateTime())
                    // для поиска эмитентов - всё описание, не только анонс
                    .setBodyForMatching(description));
        } catch (final DateTimeParseException e) {
            log.warn("News {} of {} has wrong date '{}'", guid, source, published);
            return Optional.empty();
        }
    }
}
