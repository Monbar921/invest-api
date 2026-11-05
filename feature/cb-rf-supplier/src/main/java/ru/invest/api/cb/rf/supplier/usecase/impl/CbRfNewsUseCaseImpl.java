package ru.invest.api.cb.rf.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ru.invest.api.cb.rf.supplier.client.feign.CbRfClient;
import ru.invest.api.cb.rf.supplier.usecase.CbRfNewsUseCase;
import ru.invest.api.common.model.news.KeyRateModel;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.util.RssReader;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        final Document document = RssReader.parse(cbRfClient.callDailyInfo(KEY_RATE_ACTION,
                KEY_RATE_ENVELOPE.formatted(from, to)));
        final NodeList rates = document.getElementsByTagName("KR");

        return IntStream.range(0, rates.getLength())
                .mapToObj(index -> (Element) rates.item(index))
                .map(rate -> new KeyRateModel(
                        OffsetDateTime.parse(RssReader.text(rate, "DT")).toLocalDate(),
                        new BigDecimal(RssReader.text(rate, "Rate"))))
                .sorted(Comparator.comparing(KeyRateModel::getDate))
                .toList();
    }

    /**
     * Одна лента не загрузилась - вторая всё равно полезна.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private List<NewsModel> readRss(final NewsSource source, final Supplier<byte[]> rss) {
        try {
            return RssReader.read(source, rss.get());
        } catch (final RuntimeException e) {
            log.warn("CB RF RSS {} is not loaded: {}", source, e.getMessage());
            return List.of();
        }
    }
}
