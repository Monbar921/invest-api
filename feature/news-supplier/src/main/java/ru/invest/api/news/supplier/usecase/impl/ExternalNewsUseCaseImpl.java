package ru.invest.api.news.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.util.RssReader;
import ru.invest.api.news.supplier.client.http.NewsHttpClient;
import ru.invest.api.news.supplier.config.NewsProperties;
import ru.invest.api.news.supplier.usecase.ExternalNewsUseCase;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalNewsUseCaseImpl implements ExternalNewsUseCase {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    // НКР отдаёт все релизы с 2019 года одной страницей - новые сверху, нужны только последние
    private static final int MAX_RATING_RELEASES = 50;
    private static final Pattern ACRA_DATE = Pattern.compile("(?<day>\\d{1,2})\\s+(?<month>\\p{L}+)\\.?\\s+(?<year>\\d{4})");
    private static final Pattern NKR_DATE = Pattern.compile("\\d{2}\\.\\d{2}\\.\\d{4}");
    private static final DateTimeFormatter NKR_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern ID_IN_PATH = Pattern.compile("/([^/]+)/?$");
    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("янв", 1), Map.entry("фев", 2), Map.entry("мар", 3), Map.entry("апр", 4), Map.entry("май", 5),
            Map.entry("мая", 5), Map.entry("июн", 6), Map.entry("июл", 7), Map.entry("авг", 8), Map.entry("сен", 9),
            Map.entry("окт", 10), Map.entry("ноя", 11), Map.entry("дек", 12));
    private static final int MONTH_PREFIX = 3;

    private final NewsHttpClient newsHttpClient;
    private final NewsProperties newsProperties;

    @Override
    public List<NewsModel> getMediaNews() {
        final List<NewsModel> news = new ArrayList<>();
        newsProperties.getFeeds().forEach(feed -> load(feed.getSource().name(), () ->
                RssReader.read(feed.getSource(), newsHttpClient.get(feed.getUrl()))
                        .stream()
                        .map(item -> item.setRequiresMatch(feed.isOnlyMatched()))
                        .toList())
                .forEach(news::add));
        return news;
    }

    @Override
    public List<NewsModel> getRatingNews() {
        final List<NewsModel> news = new ArrayList<>();
        if (StringUtils.isNotBlank(newsProperties.getAcraUrl())) {
            news.addAll(load("ACRA", () -> parseAcra(page(newsProperties.getAcraUrl()))));
        }
        if (StringUtils.isNotBlank(newsProperties.getNkrUrl())) {
            news.addAll(load("NKR", () -> parseNkr(page(newsProperties.getNkrUrl()))));
        }
        return news;
    }

    /**
     * Строка списка АКРА: эмитент (.item__emit), заголовок-ссылка (a.item__title), дата «5 ноя 2026».
     */
    private List<NewsModel> parseAcra(final Document page) {
        return page.select(".documents-row").stream()
                .limit(MAX_RATING_RELEASES)
                .map(row -> {
                    final Element link = row.selectFirst("a.item__title");
                    final Element date = row.selectFirst("[data-type=date]");
                    if (link == null || date == null) {
                        return Optional.<NewsModel>empty();
                    }
                    final String issuer = Optional.ofNullable(row.selectFirst(".item__emit")).map(Element::text).orElse("");
                    return acraDate(date.text()).map(day -> release(NewsSource.ACRA, link, day, issuer));
                })
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Строка таблицы НКР: заголовок-ссылка (a.blue-link), дата «23.07.2026» в одной из ячеек.
     */
    private List<NewsModel> parseNkr(final Document page) {
        return page.select("a.blue-link[href*=/press-releases/]").stream()
                .limit(MAX_RATING_RELEASES)
                .map(link -> {
                    final Element row = link.closest("tr");
                    final Matcher date = NKR_DATE.matcher(row == null ? "" : row.text());
                    if (!date.find()) {
                        return Optional.<NewsModel>empty();
                    }
                    try {
                        return Optional.of(release(NewsSource.NKR, link, LocalDate.parse(date.group(), NKR_DATE_FORMAT), ""));
                    } catch (final DateTimeParseException e) {
                        return Optional.<NewsModel>empty();
                    }
                })
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * У релизов агентств нет времени - публикация в начале дня по Москве. Эмитент из списка АКРА добавляется к тексту
     * для поиска: в заголовке он бывает в другом падеже.
     */
    private NewsModel release(final NewsSource source, final Element link, final LocalDate day, final String issuer) {
        final String url = link.absUrl("href");
        final Matcher id = ID_IN_PATH.matcher(url);
        return new NewsModel()
                .setSource(source)
                .setExternalId(StringUtils.left(id.find() ? id.group(1) : url, RssReader.EXTERNAL_ID_LENGTH))
                .setTitle(link.text())
                .setUrl(url)
                .setPublishedAt(day.atStartOfDay(MOSCOW).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .setBodyForMatching(issuer);
    }

    private Optional<LocalDate> acraDate(final String text) {
        final Matcher matcher = ACRA_DATE.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        final String month = matcher.group("month").toLowerCase(Locale.ROOT);
        final Integer number = MONTHS.get(month.substring(0, Math.min(MONTH_PREFIX, month.length())));
        return number == null ? Optional.empty()
                : Optional.of(LocalDate.of(Integer.parseInt(matcher.group("year")), number, Integer.parseInt(matcher.group("day"))));
    }

    private Document page(final String url) {
        final byte[] body = newsHttpClient.get(url);
        if (body == null || body.length == 0) {
            throw new IllegalStateException("Empty response from " + url);
        }
        try {
            // кодировку jsoup берёт из meta страницы
            return Jsoup.parse(new ByteArrayInputStream(body), null, url);
        } catch (final IOException e) {
            throw new IllegalStateException("Page " + url + " is not parsed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private List<NewsModel> load(final String source, final Supplier<List<NewsModel>> loader) {
        try {
            final List<NewsModel> news = loader.get();
            log.info("News source {}: {} items", source, news.size());
            return news;
        } catch (final RuntimeException e) {
            log.warn("News source {} is not loaded: {}", source, e.getMessage());
            return List.of();
        }
    }
}
