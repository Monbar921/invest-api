package ru.invest.api.alert.service.usecase.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.alert.service.config.AlertProperties;
import ru.invest.api.alert.service.model.RiskKind;
import ru.invest.api.alert.service.usecase.PortfolioRiskCheckUseCase;
import ru.invest.api.common.entity.BondAlert;
import ru.invest.api.common.entity.BondIssuer;
import ru.invest.api.common.entity.News;
import ru.invest.api.common.entity.NewsLink;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.repository.BondAlertRepository;
import ru.invest.api.common.repository.BondIssuerRepository;
import ru.invest.api.common.repository.NewsRepository;
import ru.invest.api.common.repository.NotificationRepository;
import ru.invest.api.news.supplier.usecase.impl.IssuerTitles;
import ru.invest.api.portfolio.service.model.PortfolioGroupModel;
import ru.invest.api.portfolio.service.usecase.PortfolioUseCase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Монитор риска. Новости читаются один раз на всех пользователей; портфель пользователя - только если среди новых
 * новостей есть рисковые. Одно событие - одно уведомление на пользователя: облигации одного эмитента собираются в одно
 * сообщение, а то же событие из других источников (биржа, агентство, СМИ) в течение quiet-days не повторяется.
 */
@Slf4j
@Service
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public class PortfolioRiskCheckUseCaseImpl implements PortfolioRiskCheckUseCase {
    private static final Set<AlertType> TYPES = Set.of(AlertType.PORTFOLIO_RISK);

    private final AlertProperties properties;
    private final BondAlertRepository alertRepository;
    private final NewsRepository newsRepository;
    private final BondIssuerRepository issuerRepository;
    private final NotificationRepository notificationRepository;
    private final PortfolioUseCase portfolioUseCase;
    private final NotificationQueue notificationQueue;
    private final TransactionTemplate transaction;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public PortfolioRiskCheckUseCaseImpl(final AlertProperties properties,
                                         final BondAlertRepository alertRepository,
                                         final NewsRepository newsRepository,
                                         final BondIssuerRepository issuerRepository,
                                         final NotificationRepository notificationRepository,
                                         final PortfolioUseCase portfolioUseCase,
                                         final NotificationQueue notificationQueue,
                                         final PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.alertRepository = alertRepository;
        this.newsRepository = newsRepository;
        this.issuerRepository = issuerRepository;
        this.notificationRepository = notificationRepository;
        this.portfolioUseCase = portfolioUseCase;
        this.notificationQueue = notificationQueue;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public int checkAll() {
        final List<BondAlert> alerts = alertRepository.findByEnabledTrueAndTypeIn(TYPES);
        if (alerts.isEmpty()) {
            return 0;
        }
        final long latest = newsRepository.findMaxId().orElse(0L);
        final long from = alerts.stream()
                .map(BondAlert::getCursorId)
                .filter(Objects::nonNull)
                .min(Long::compare)
                .orElse(latest);
        final List<Long> ids = newsRepository.findIdsAfter(from, Limit.of(properties.getRisk().getScanLimit()));
        // просмотрено до последней прочитанной: если новостей больше лимита, остальные - в следующую проверку
        final long scanned = ids.isEmpty() ? from : ids.getLast();
        final List<RiskNews> risks = ids.isEmpty() ? List.of() : risks(ids);

        int queued = 0;
        for (final BondAlert found : alerts) {
            try {
                final List<RiskNews> fresh = found.getCursorId() == null
                        ? List.of()
                        : risks.stream().filter(news -> news.id() > found.getCursorId()).toList();
                // портфель читается до транзакции правила: его чтение - своя read-only транзакция
                final List<Holding> holdings = fresh.isEmpty() ? List.of() : holdings(found.getUserId());
                queued += Optional.ofNullable(transaction.execute(status -> alertRepository.findById(found.getId())
                        .filter(BondAlert::isEnabled)
                        .map(alert -> check(alert, fresh, holdings, alert.getCursorId() == null ? latest : scanned))
                        .orElse(0))).orElse(0);
            } catch (final RuntimeException e) {
                log.error("Portfolio risk check failed for alert {}", found.getId(), e);
            }
        }
        log.info("Portfolio risk check: {} rules, {} risk news, {} notifications queued", alerts.size(), risks.size(),
                queued);
        return queued;
    }

    /**
     * @param cursor докуда отметить просмотренным; правило без отметки начинает с последней загруженной новости
     */
    private int check(final BondAlert alert, final List<RiskNews> news, final List<Holding> holdings,
                      final long cursor) {
        if (alert.getCursorId() == null) {
            alert.setCursorId(cursor);
            return 0;
        }
        alert.setCursorId(Math.max(alert.getCursorId(), cursor));
        if (news.isEmpty() || holdings.isEmpty()) {
            return 0;
        }
        final List<RiskEvent> events = news.stream()
                .flatMap(item -> events(item, holdings).stream())
                .toList();
        // много событий разом (первая загрузка ленты) - только последние
        final int max = properties.getRisk().getMaxPerCheck();
        int queued = 0;
        for (final RiskEvent event : events.subList(Math.max(0, events.size() - max), events.size())) {
            if (enqueue(alert, event)) {
                queued++;
            }
        }
        if (queued > 0) {
            alert.setLastTriggeredAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        return queued;
    }

    /**
     * Событие на каждого эмитента портфеля, которого касается новость. Приостановка торгов касается выпуска: если в
     * новости названы выпуски, другие выпуски того же эмитента ею не затронуты.
     */
    private List<RiskEvent> events(final RiskNews news, final List<Holding> holdings) {
        final boolean byIssueOnly = news.kind() == RiskKind.TRADING_SUSPENSION && !news.isins().isEmpty();
        final Map<String, List<Holding>> byEmitter = holdings.stream()
                .filter(holding -> news.isins().contains(holding.isin())
                        || !byIssueOnly && holding.emitterId() != null && news.emitterIds().contains(holding.emitterId()))
                .collect(Collectors.groupingBy(Holding::emitterKey, LinkedHashMap::new, Collectors.toList()));
        return byEmitter.entrySet().stream()
                .map(entry -> new RiskEvent(news, entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Ключ: правило, вид риска (понижение рейтинга - ещё и агентство), эмитент и день новости по Москве. Тот же ключ в
     * пределах quiet-days дней от этого дня - событие уже было, второе сообщение не нужно.
     */
    private boolean enqueue(final BondAlert alert, final RiskEvent event) {
        final RiskNews news = event.news();
        final String kind = news.kind() == RiskKind.RATING_DOWNGRADE
                ? news.kind() + "-" + news.source()
                : news.kind().name();
        final LocalDate day = Optional.ofNullable(news.publishedAt())
                .map(time -> time.atOffset(ZoneOffset.UTC).atZoneSameInstant(properties.getZone()).toLocalDate())
                .orElseGet(() -> LocalDate.now(properties.getZone()));
        final Function<LocalDate, String> key = date -> "%s:%d:%s:%s:%s".formatted(AlertType.PORTFOLIO_RISK,
                alert.getId(), kind, event.emitterKey(), date);
        // в обе стороны: релиз агентства с датой без времени может прийти позже новости биржи следующего дня
        final int quiet = Math.max(1, properties.getRisk().getQuietDays()) - 1;
        final boolean seen = IntStream.rangeClosed(-quiet, quiet)
                .filter(offset -> offset != 0)
                .mapToObj(day::plusDays)
                .map(key)
                .anyMatch(notificationRepository::existsByDedupKey);
        if (seen) {
            return false;
        }
        final Holding first = event.holdings().getFirst();
        return notificationQueue.enqueue(alert.getUserId(), alert.getId(),
                event.holdings().size() == 1 ? first.ticker() : null, key.apply(day),
                AlertTexts.risk(news.kind(), first.emitterTitle(),
                        event.holdings().stream().map(holding -> AlertTexts.bond(holding.name(), holding.ticker()))
                                .toList(),
                        news.title(), news.source(), news.publishedAt(), news.url()));
    }

    /** Рисковые новости среди новых, по возрастанию id */
    private List<RiskNews> risks(final List<Long> ids) {
        return newsRepository.findWithLinksByIdIn(ids).stream()
                .sorted(Comparator.comparing(News::getId))
                .flatMap(news -> RiskEventClassifier.classify(news.getSource(), news.getTitle())
                        .map(kind -> new RiskNews(news.getId(), kind, news.getSource(), news.getTitle(), news.getUrl(),
                                news.getPublishedAt(),
                                news.getLinks().stream().map(NewsLink::getIsin).filter(Objects::nonNull)
                                        .collect(Collectors.toSet()),
                                news.getLinks().stream().map(NewsLink::getEmitterId).filter(Objects::nonNull)
                                        .collect(Collectors.toSet())))
                        .stream())
                .toList();
    }

    /** Открытые позиции пользователя с эмитентом; облигаций не из системы (без ISIN) монитор не видит */
    private List<Holding> holdings(final String userId) {
        final List<PortfolioGroupModel> groups = portfolioUseCase.getPortfolio(userId, null).groups().stream()
                .filter(group -> group.isin() != null)
                .toList();
        if (groups.isEmpty()) {
            return List.of();
        }
        final Map<String, BondIssuer> issuers = issuerRepository.findByIsinIn(groups.stream()
                        .map(PortfolioGroupModel::isin).toList()).stream()
                .collect(Collectors.toMap(BondIssuer::getIsin, Function.identity(), (first, second) -> first));
        return groups.stream()
                .map(group -> {
                    final BondIssuer issuer = issuers.get(group.isin());
                    return new Holding(group.ticker(), group.bondName(), group.isin(),
                            issuer == null ? null : issuer.getEmitterId(),
                            issuer == null ? null : IssuerTitles.shortTitle(issuer.getTitle()));
                })
                .toList();
    }

    private record RiskNews(Long id, RiskKind kind, NewsSource source, String title, String url,
                            LocalDateTime publishedAt, Set<String> isins, Set<Long> emitterIds) {
    }

    private record Holding(String ticker, String name, String isin, Long emitterId, String emitterTitle) {
        /** Эмитент неизвестен - выпуск сам себе эмитент */
        String emitterKey() {
            return emitterId == null ? "isin-" + isin : "emitter-" + emitterId;
        }
    }

    private record RiskEvent(RiskNews news, String emitterKey, List<Holding> holdings) {
    }
}
