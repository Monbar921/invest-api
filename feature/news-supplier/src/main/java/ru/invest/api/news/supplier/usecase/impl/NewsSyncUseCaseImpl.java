package ru.invest.api.news.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
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
import ru.invest.api.common.model.event.NewsSyncedEvent;
import ru.invest.api.common.model.news.IssuerModel;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.repository.BondIssuerRepository;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.NewsRepository;
import ru.invest.api.news.supplier.usecase.ExternalNewsUseCase;
import ru.invest.api.news.supplier.usecase.MoexNewsUseCase;
import ru.invest.api.news.supplier.usecase.NewsSyncUseCase;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsSyncUseCaseImpl implements NewsSyncUseCase {
    // эмитенты меняются редко, а их список - десятки запросов к бирже
    private static final Duration ISSUER_REFRESH_PERIOD = Duration.ofHours(20);
    private static final Duration NEWS_RETENTION = Duration.ofDays(180);

    private final CbRfNewsUseCase cbRfNewsUseCase;
    private final MoexNewsUseCase moexNewsUseCase;
    private final ExternalNewsUseCase externalNewsUseCase;
    private final NewsRepository newsRepository;
    private final BondIssuerRepository bondIssuerRepository;
    private final BondRepository bondRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditMapper auditMapper;
    private final DateTimeMapper dateTimeMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void syncAll(final AuditModel audit) {
        // шаги независимы: недоступный источник не мешает загрузить остальные
        runStep("issuers", () -> refreshIssuersIfStale(audit));
        final IssuerMatcher matcher = new IssuerMatcher(bondIssuerRepository.findAll());
        saveStep("cbr", cbRfNewsUseCase::getNews, matcher, audit);
        saveStep("moex", () -> moexNewsUseCase.getNewsAfter(newsRepository.findLastMoexId().orElse(null)), matcher, audit);
        saveStep("ratings", externalNewsUseCase::getRatingNews, matcher, audit);
        saveStep("media", externalNewsUseCase::getMediaNews, matcher, audit);
        runStep("cleanup", () -> {
            final Integer deleted = transactionTemplate.execute(status ->
                    newsRepository.deletePublishedBefore(dateTimeMapper.getNow().minus(NEWS_RETENTION)));
            log.info("News older than {} days deleted: {}", NEWS_RETENTION.toDays(), deleted);
        });
        // после загрузки: правила уведомлений по новостям видят свежие новости
        eventPublisher.publishEvent(new NewsSyncedEvent());
    }

    private void saveStep(final String step, final Supplier<List<NewsModel>> news, final IssuerMatcher matcher,
                          final AuditModel audit) {
        runStep(step, () -> save(news.get(), matcher, audit));
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

    private void save(final List<NewsModel> news, final IssuerMatcher matcher, final AuditModel audit) {
        final Map<NewsSource, List<NewsModel>> bySource = news.stream()
                .collect(Collectors.groupingBy(NewsModel::getSource));
        final Audit created = auditMapper.toEntity(audit);

        bySource.forEach((source, items) -> {
            final Set<String> known = newsRepository.findBySourceAndExternalIdIn(source,
                            items.stream().map(NewsModel::getExternalId).toList())
                    .stream()
                    .map(News::getExternalId)
                    .collect(Collectors.toSet());
            final Map<String, News> fresh = new HashMap<>();
            int skipped = 0;
            for (final NewsModel item : items) {
                if (known.contains(item.getExternalId()) || fresh.containsKey(item.getExternalId())) {
                    continue;
                }
                final String text = item.getTitle() + " " + StringUtils.defaultString(item.getBodyForMatching());
                final Set<String> isins = new LinkedHashSet<>(item.getIsins());
                isins.addAll(IssuerMatcher.isins(text));
                final Set<Long> emitters = matcher.emitters(text, isins);
                // общая лента СМИ: без выпуска или эмитента из системы новость не нужна
                if (item.isRequiresMatch() && emitters.isEmpty() && isins.stream().noneMatch(matcher::isKnownIsin)) {
                    skipped++;
                    continue;
                }
                fresh.put(item.getExternalId(), toEntity(item, isins, emitters, created));
            }

            newsRepository.saveAll(fresh.values());
            log.info("News {}: {} received, {} new, {} without our bonds skipped", source, items.size(), fresh.size(), skipped);
        });
    }

    private News toEntity(final NewsModel model, final Collection<String> isins, final Collection<Long> emitterIds,
                          final Audit created) {
        final News news = new News()
                .setSource(model.getSource())
                .setExternalId(model.getExternalId())
                .setTitle(model.getTitle())
                .setSummary(model.getSummary())
                .setUrl(model.getUrl())
                .setPublishedAt(model.getPublishedAt())
                .setCreated(created);
        isins.forEach(isin -> news.getLinks().add(new NewsLink().setNews(news).setIsin(isin)));
        emitterIds.forEach(emitterId -> news.getLinks().add(new NewsLink().setNews(news).setEmitterId(emitterId)));
        return news;
    }
}
