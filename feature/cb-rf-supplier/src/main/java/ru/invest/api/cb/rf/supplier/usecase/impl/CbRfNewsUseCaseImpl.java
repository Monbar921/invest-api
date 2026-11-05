package ru.invest.api.cb.rf.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;
import ru.invest.api.cb.rf.supplier.usecase.CbRfNewsUseCase;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.news.KeyRateModel;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.util.HtmlText;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CbRfNewsUseCaseImpl implements CbRfNewsUseCase {
    private static final String KEY_RATE_ACTION = "\"http://web.cbr.ru/KeyRateXML\"";
    private static final String KEY_RATE_ENVELOPE = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
              <soap:Body>
                <KeyRateXML xmlns="http://web.cbr.ru/"><fromDate>%s</fromDate><ToDate>%s</ToDate></KeyRateXML>
              </soap:Body>
            </soap:Envelope>
            """;
    // анонс пресс-релиза - начало текста; полный текст не перепечатываем, по ссылке он есть у ЦБ
    private static final int SUMMARY_LENGTH = 300;

    private final CbRfClient cbRfClient;

    @Override
    public List<NewsModel> getNews() {
        final List<NewsModel> news = new ArrayList<>();
        news.addAll(readRss(NewsSource.CBR_PRESS, cbRfClient::getPressReleasesRss));
        news.addAll(readRss(NewsSource.CBR_EVENT, cbRfClient::getEventsRss));
        return news;
    }

    @Override
    public List<KeyRateModel> getKeyRates(final LocalDate from, final LocalDate to) {
        final Document document = parse(cbRfClient.callDailyInfo(KEY_RATE_ACTION, KEY_RATE_ENVELOPE.formatted(from, to)));
        final NodeList rates = document.getElementsByTagName("KR");

        return IntStream.range(0, rates.getLength())
                .mapToObj(index -> (Element) rates.item(index))
                .map(rate -> new KeyRateModel(
                        OffsetDateTime.parse(text(rate, "DT")).toLocalDate(),
                        new BigDecimal(text(rate, "Rate"))))
                .sorted(Comparator.comparing(KeyRateModel::getDate))
                .toList();
    }

    /**
     * Одна лента не загрузилась - вторая всё равно полезна.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private List<NewsModel> readRss(final NewsSource source, final Supplier<byte[]> rss) {
        try {
            final NodeList items = parse(rss.get()).getElementsByTagName("item");
            return IntStream.range(0, items.getLength())
                    .mapToObj(index -> toNews(source, (Element) items.item(index)))
                    .flatMap(Optional::stream)
                    .toList();
        } catch (final RuntimeException e) {
            log.warn("CB RF RSS {} is not loaded: {}", source, e.getMessage());
            return List.of();
        }
    }

    private Optional<NewsModel> toNews(final NewsSource source, final Element item) {
        final String guid = StringUtils.defaultIfBlank(text(item, "guid"), text(item, "link"));
        final String title = HtmlText.toPlainText(text(item, "title"));
        final String published = text(item, "pubDate");
        if (StringUtils.isAnyBlank(guid, title, published)) {
            return Optional.empty();
        }

        try {
            return Optional.of(new NewsModel()
                    .setSource(source)
                    .setExternalId(guid)
                    .setTitle(title)
                    .setSummary(HtmlText.abbreviate(HtmlText.toPlainText(text(item, "description")), SUMMARY_LENGTH))
                    .setUrl(StringUtils.trimToNull(text(item, "link")))
                    .setPublishedAt(OffsetDateTime.parse(published, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .withOffsetSameInstant(ZoneOffset.UTC)
                            .toLocalDateTime()));
        } catch (final DateTimeParseException e) {
            log.warn("CB RF news {} has wrong date '{}'", guid, published);
            return Optional.empty();
        }
    }

    private String text(final Element element, final String tag) {
        final NodeList nodes = element.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : StringUtils.trimToNull(nodes.item(0).getTextContent());
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private Document parse(final byte[] xml) {
        if (xml == null || xml.length == 0) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.EXTERNAL_SERVICE_ERROR, "Empty response from CB RF");
        }
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // ответ внешнего сервиса: без DTD и внешних сущностей (XXE)
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        } catch (final Exception e) {
            throw new GeneralUnprocessableEntityException(ExceptionErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Wrong XML from CB RF: " + e.getMessage());
        }
    }
}
