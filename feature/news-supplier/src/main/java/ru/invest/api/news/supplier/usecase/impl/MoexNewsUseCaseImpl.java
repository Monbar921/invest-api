package ru.invest.api.news.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.model.news.IssuerModel;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.util.HtmlText;
import ru.invest.api.news.supplier.client.feign.MoexIssClient;
import ru.invest.api.news.supplier.model.IssTableDto;
import ru.invest.api.news.supplier.usecase.MoexNewsUseCase;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class MoexNewsUseCaseImpl implements MoexNewsUseCase {
    private static final String NEWS_URL = "https://www.moex.com/n%d";
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter ISS_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int NEWS_PAGE_SIZE = 50;
    private static final int SECURITIES_PAGE_SIZE = 100;
    // первая загрузка - примерно неделя новостей; дальше страниц нужно одна-две: новости идут раз в 15 минут
    private static final int MAX_NEWS_PAGES = 4;
    // ~3 тысячи торгуемых облигаций - 30 страниц; предел на случай, если ISS перестанет отдавать пустую страницу
    private static final int MAX_SECURITIES_PAGES = 100;
    private static final Pattern ISIN = Pattern.compile("\\b[A-Z]{2}[0-9A-Z]{9}[0-9]\\b");
    /**
     * Технические сообщения: их десятки в день, инвестору в облигации они ничего не говорят.
     */
    private static final Pattern NOISE = Pattern.compile(String.join("|",
            "ценового коридора", "оценки рыночных рисков", "ставки переноса", "дестабилизации цен",
            "дискретный аукцион", "депозитный аукцион", "УФК", "РЕПО", "фьючерс", "индексов Московской биржи",
            "тестовом контуре", "шлюзов", "отчётах по итогам", "отчетах по итогам", "Расписание торгов",
            "О начале торгов", "шага цены", "инвестиционных па", "Определена цена исполнения",
            "Определены цены исполнения", "Технические работы", "риск-параметр", "баз расчета индексов",
            "баз расчёта индексов"), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final MoexIssClient moexIssClient;

    @Override
    public List<NewsModel> getNewsAfter(final Long lastKnownId) {
        final List<NewsModel> news = new ArrayList<>();
        for (int page = 0; page < MAX_NEWS_PAGES; page++) {
            final List<Map<String, Object>> rows = block(moexIssClient.getSiteNews(Map.of(
                    "iss.meta", "off", "start", String.valueOf(page * NEWS_PAGE_SIZE))), "sitenews");
            boolean reachedKnown = rows.isEmpty();
            for (final Map<String, Object> row : rows) {
                final long id = ((Number) row.get("id")).longValue();
                if (lastKnownId != null && id <= lastKnownId) {
                    reachedKnown = true;
                    continue;
                }
                toNews(id, row).ifPresent(news::add);
            }
            if (reachedKnown) {
                break;
            }
        }
        return news;
    }

    @Override
    public List<IssuerModel> getBondIssuers() {
        final List<IssuerModel> issuers = new ArrayList<>();
        for (int page = 0; page < MAX_SECURITIES_PAGES; page++) {
            final List<Map<String, Object>> rows = block(moexIssClient.getSecurities(Map.of(
                    "iss.meta", "off",
                    "engine", "stock",
                    "market", "bonds",
                    "is_trading", "1",
                    "securities.columns", "isin,emitent_id,emitent_title,emitent_inn",
                    "start", String.valueOf(page * SECURITIES_PAGE_SIZE))), "securities");
            if (rows.isEmpty()) {
                break;
            }
            rows.stream()
                    .filter(row -> row.get("isin") != null && row.get("emitent_id") instanceof Number)
                    .map(row -> new IssuerModel(
                            String.valueOf(row.get("isin")),
                            ((Number) row.get("emitent_id")).longValue(),
                            (String) row.get("emitent_title"),
                            (String) row.get("emitent_inn")))
                    .forEach(issuers::add);
        }
        return issuers;
    }

    private Optional<NewsModel> toNews(final long id, final Map<String, Object> row) {
        final String title = HtmlText.toPlainText((String) row.get("title"));
        final String published = (String) row.get("published_at");
        if (StringUtils.isAnyBlank(title, published) || NOISE.matcher(title).find()) {
            return Optional.empty();
        }

        // ISIN часто только в тексте: «О приостановке торгов ценными бумагами» перечисляет выпуски там
        final String body = loadBody(id);
        final Set<String> isins = new LinkedHashSet<>();
        final Matcher matcher = ISIN.matcher(title + " " + StringUtils.defaultString(body));
        while (matcher.find()) {
            isins.add(matcher.group());
        }

        return Optional.of(new NewsModel()
                .setSource(NewsSource.MOEX)
                .setExternalId(String.valueOf(id))
                .setTitle(title)
                // текст сообщений биржи начинается с обращения «Участникам торгов» - как анонс он ничего не даёт
                .setUrl(NEWS_URL.formatted(id))
                .setPublishedAt(LocalDateTime.parse(published, ISS_DATE_TIME)
                        .atZone(MOSCOW)
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime())
                .setIsins(isins)
                .setBodyForMatching(StringUtils.defaultString(body)));
    }

    /**
     * Нет текста - новость всё равно сохраняется, просто связи с выпусками ищутся только по заголовку.
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    private String loadBody(final long id) {
        try {
            return block(moexIssClient.getSiteNewsContent(id, Map.of("iss.meta", "off")), "content")
                    .stream()
                    .findFirst()
                    .map(row -> HtmlText.toPlainText((String) row.get("body")))
                    .orElse(null);
        } catch (final RuntimeException e) {
            log.warn("MOEX news {} text is not loaded: {}", id, e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> block(final Map<String, IssTableDto> response, final String name) {
        return Optional.ofNullable(response)
                .map(blocks -> blocks.get(name))
                .map(IssTableDto::rows)
                .orElse(List.of());
    }
}
