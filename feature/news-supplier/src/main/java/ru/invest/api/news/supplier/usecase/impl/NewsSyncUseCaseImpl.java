package ru.invest.api.news.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.cb.rf.supplier.usecase.CbRfNewsUseCase;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.entity.BondIssuer;
import ru.invest.api.common.entity.News;
import ru.invest.api.common.entity.NewsLink;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.news.IssuerModel;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.repository.BondIssuerRepository;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.NewsRepository;
import ru.invest.api.news.supplier.usecase.MoexNewsUseCase;
import ru.invest.api.news.supplier.usecase.NewsSyncUseCase;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsSyncUseCaseImpl implements NewsSyncUseCase {
    // эмитенты меняются редко, а их список - десятки запросов к бирже
    private static final Duration ISSUER_REFRESH_PERIOD = Duration.ofHours(20);
    private static final Duration NEWS_RETENTION = Duration.ofDays(180);
    // короче - слишком много случайных совпадений
    private static final int MIN_ISSUER_NAME_LENGTH = 3;
    private static final char QUOTE = '"';

    private final CbRfNewsUseCase cbRfNewsUseCase;
    private final MoexNewsUseCase moexNewsUseCase;
    private final NewsRepository newsRepository;
    private final BondIssuerRepository bondIssuerRepository;
    private final BondRepository bondRepository;
    private final AuditMapper auditMapper;
    private final DateTimeMapper dateTimeMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void syncAll(final AuditModel audit) {
        // шаги независимы: недоступная биржа не мешает загрузить новости ЦБ
        runStep("issuers", () -> refreshIssuersIfStale(audit));
        runStep("cbr", () -> save(cbRfNewsUseCase.getNews(), Map.of(), audit));
        runStep("moex", () -> save(moexNewsUseCase.getNewsAfter(newsRepository.findLastMoexId().orElse(null)),
                issuerNames(), audit));
        runStep("cleanup", () -> {
            final Integer deleted = transactionTemplate.execute(status ->
                    newsRepository.deletePublishedBefore(dateTimeMapper.getNow().minus(NEWS_RETENTION)));
            log.info("News older than {} days deleted: {}", NEWS_RETENTION.toDays(), deleted);
        });
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    private void runStep(final String step, final Runnable action) {
        try {
            action.run();
        } catch (final RuntimeException e) {
            log.error("News sync step '{}' failed: {}", step, e.getMessage(), e);
        }
    }

    private void refreshIssuersIfStale(final AuditModel audit) {
        final LocalDateTime lastUpdate = bondIssuerRepository.findLastUpdate().orElse(null);
        if (lastUpdate != null && lastUpdate.isAfter(dateTimeMapper.getNow().minus(ISSUER_REFRESH_PERIOD))) {
            return;
        }

        // эмитенты нужны только выпускам из системы
        final Set<String> ourIsins = bondRepository.findAllIsins();
        final Map<String, IssuerModel> issuers = moexNewsUseCase.getBondIssuers()
                .stream()
                .filter(issuer -> ourIsins.contains(issuer.getIsin()))
                .collect(Collectors.toMap(IssuerModel::getIsin, Function.identity(), (first, second) -> first));
        if (issuers.isEmpty()) {
            log.warn("MOEX returned no issuers for our bonds, issuers are kept");
            return;
        }

        final Audit updated = auditMapper.toEntity(audit);
        final Map<String, BondIssuer> existing = bondIssuerRepository.findAll()
                .stream()
                .collect(Collectors.toMap(BondIssuer::getIsin, Function.identity()));
        final List<BondIssuer> toSave = issuers.values()
                .stream()
                .map(issuer -> existing.getOrDefault(issuer.getIsin(), new BondIssuer().setIsin(issuer.getIsin()))
                        .setEmitterId(issuer.getEmitterId())
                        .setTitle(issuer.getTitle())
                        .setInn(issuer.getInn())
                        .setUpdated(updated))
                .toList();
        final List<BondIssuer> toDelete = existing.values()
                .stream()
                .filter(issuer -> !issuers.containsKey(issuer.getIsin()))
                .toList();

        transactionTemplate.executeWithoutResult(status -> {
            bondIssuerRepository.deleteAll(toDelete);
            bondIssuerRepository.saveAll(toSave);
        });
        log.info("Bond issuers refreshed: {} saved, {} deleted", toSave.size(), toDelete.size());
    }

    /**
     * Эмитент -> название из кавычек в нижнем регистре: «ПАО "Селигдар"» -> «селигдар». Ищется в тексте тоже
     * в кавычках, иначе «Россия» или «Транс» находились бы где угодно. Эмитенты без кавычек в названии
     * (министерства, области) находятся только по ISIN.
     */
    private Map<Long, String> issuerNames() {
        final Map<Long, String> names = new HashMap<>();
        bondIssuerRepository.findAll().forEach(issuer -> {
            final String name = quotedName(issuer.getTitle());
            if (name != null) {
                names.putIfAbsent(issuer.getEmitterId(), name);
            }
        });
        return names;
    }

    private String quotedName(final String title) {
        final String normalized = normalizeQuotes(title);
        if (normalized == null) {
            return null;
        }
        final int first = normalized.indexOf(QUOTE);
        final int last = normalized.lastIndexOf(QUOTE);
        if (first < 0 || last <= first) {
            return null;
        }
        final String name = StringUtils.trimToNull(normalized.substring(first + 1, last));
        return name == null || name.length() < MIN_ISSUER_NAME_LENGTH ? null : name.toLowerCase(Locale.ROOT);
    }

    private String normalizeQuotes(final String text) {
        return text == null ? null : StringUtils.replaceChars(text, "«»“”„", "\"\"\"\"\"");
    }

    private void save(final List<NewsModel> news, final Map<Long, String> issuerNames, final AuditModel audit) {
        final Map<NewsSource, List<NewsModel>> bySource = news.stream()
                .collect(Collectors.groupingBy(NewsModel::getSource));
        final Audit created = auditMapper.toEntity(audit);
        final Map<String, Long> emitterByIsin = bondIssuerRepository.findAll()
                .stream()
                .collect(Collectors.toMap(BondIssuer::getIsin, BondIssuer::getEmitterId, (first, second) -> first));

        bySource.forEach((source, items) -> {
            final Set<String> known = newsRepository.findBySourceAndExternalIdIn(source,
                            items.stream().map(NewsModel::getExternalId).toList())
                    .stream()
                    .map(News::getExternalId)
                    .collect(Collectors.toSet());
            final Map<String, News> fresh = new HashMap<>();
            items.stream()
                    .filter(item -> !known.contains(item.getExternalId()))
                    .forEach(item -> fresh.putIfAbsent(item.getExternalId(),
                            toEntity(item, emitters(item, issuerNames, emitterByIsin), created)));

            newsRepository.saveAll(fresh.values());
            log.info("News {}: {} received, {} new", source, items.size(), fresh.size());
        });
    }

    private Set<Long> emitters(final NewsModel news, final Map<Long, String> issuerNames,
                               final Map<String, Long> emitterByIsin) {
        final Set<Long> emitters = news.getIsins()
                .stream()
                .map(emitterByIsin::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!issuerNames.isEmpty()) {
            final String text = normalizeQuotes(news.getTitle() + " " + StringUtils.defaultString(news.getBodyForMatching()))
                    .toLowerCase(Locale.ROOT);
            issuerNames.forEach((emitterId, name) -> {
                if (text.contains(QUOTE + name + QUOTE)) {
                    emitters.add(emitterId);
                }
            });
        }
        return emitters;
    }

    private News toEntity(final NewsModel model, final Collection<Long> emitterIds, final Audit created) {
        final News news = new News()
                .setSource(model.getSource())
                .setExternalId(model.getExternalId())
                .setTitle(model.getTitle())
                .setSummary(model.getSummary())
                .setUrl(model.getUrl())
                .setPublishedAt(model.getPublishedAt())
                .setCreated(created);
        model.getIsins().forEach(isin -> news.getLinks().add(new NewsLink().setNews(news).setIsin(isin)));
        emitterIds.forEach(emitterId -> news.getLinks().add(new NewsLink().setNews(news).setEmitterId(emitterId)));
        return news;
    }
}
