package ru.invest.api.alert.service.usecase.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.alert.service.usecase.NewsAlertCheckUseCase;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.BondAlert;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.repository.BondAlertRepository;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.news.supplier.usecase.NewsUseCase;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NewsAlertCheckUseCaseImpl implements NewsAlertCheckUseCase {
    /** Больше уведомлений по одному правилу за раз не шлём: старые из пачки пропускаются */
    static final int MAX_PER_ALERT = 5;
    /** Сколько последних новостей просматривать за проверку: лента грузится раз в 15 минут, больше не набирается */
    private static final int PAGE_SIZE = 50;
    private static final Set<NewsSource> RATING_SOURCES = Set.of(NewsSource.ACRA, NewsSource.NKR);
    // «Банк России принял решение сохранить ключевую ставку на уровне 14,00% годовых» (так ЦБ пишет сейчас),
    // «Банк России снизил ключевую ставку на 50 б.п.» - и неопределённая форма, и прошедшее время
    private static final Pattern KEY_RATE_DECISION = Pattern.compile(
            "(снизи|повыси|сохрани)(л|ть)\\s+ключевую\\s+ставку", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Set<AlertType> NEWS_TYPES = Set.of(AlertType.BOND_NEWS, AlertType.ISSUER_RATING,
            AlertType.KEY_RATE);

    private final BondAlertRepository alertRepository;
    private final BondRepository bondRepository;
    private final NewsUseCase newsUseCase;
    private final NotificationQueue notificationQueue;
    private final TransactionTemplate transaction;

    public NewsAlertCheckUseCaseImpl(final BondAlertRepository alertRepository,
                                     final BondRepository bondRepository,
                                     final NewsUseCase newsUseCase,
                                     final NotificationQueue notificationQueue,
                                     final PlatformTransactionManager transactionManager) {
        this.alertRepository = alertRepository;
        this.bondRepository = bondRepository;
        this.newsUseCase = newsUseCase;
        this.notificationQueue = notificationQueue;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public int checkAll() {
        final List<BondAlert> alerts = alertRepository.findByEnabledTrueAndTypeIn(NEWS_TYPES);
        if (alerts.isEmpty()) {
            return 0;
        }
        // новости читаются до транзакций правил: «облигации нет» из транзакционного чтения не должно откатывать запись
        final Map<String, List<NewsModel>> byTicker = new HashMap<>();
        alerts.stream()
                .map(BondAlert::getTicker)
                .filter(ticker -> ticker != null && !byTicker.containsKey(ticker))
                .distinct()
                .forEach(ticker -> byTicker.put(ticker, bondNews(ticker)));
        final List<NewsModel> keyRateNews = alerts.stream().anyMatch(alert -> alert.getType() == AlertType.KEY_RATE)
                ? keyRateDecisions()
                : List.of();
        // у кого на облигацию включены «Новости» - рейтинговое действие придёт ими, второе сообщение не нужно
        final Set<String> newsCovered = alerts.stream()
                .filter(alert -> alert.getType() == AlertType.BOND_NEWS)
                .map(alert -> alert.getUserId() + ":" + alert.getTicker())
                .collect(Collectors.toSet());
        final Map<String, Bond> bonds = bondRepository.findByTickerIn(byTicker.keySet()).stream()
                .collect(Collectors.toMap(Bond::getTicker, Function.identity(), (first, second) -> first));

        int queued = 0;
        for (final BondAlert found : alerts) {
            try {
                queued += Optional.ofNullable(transaction.execute(status -> alertRepository.findById(found.getId())
                        .filter(BondAlert::isEnabled)
                        .map(alert -> check(alert,
                                alert.getType() == AlertType.KEY_RATE
                                        ? keyRateNews
                                        : byTicker.getOrDefault(alert.getTicker(), List.of()),
                                Optional.ofNullable(alert.getTicker()).map(bonds::get).map(Bond::getName).orElse(null),
                                alert.getType() == AlertType.ISSUER_RATING
                                        && newsCovered.contains(alert.getUserId() + ":" + alert.getTicker())))
                        .orElse(0))).orElse(0);
            } catch (final RuntimeException e) {
                log.error("News alert check failed for alert {}", found.getId(), e);
            }
        }
        log.info("News alert check: {} rules, {} notifications queued", alerts.size(), queued);
        return queued;
    }

    /**
     * @param silent только сдвинуть отметку, не уведомлять: новость уже придёт другим правилом
     */
    private int check(final BondAlert alert, final List<NewsModel> news, final String bondName,
                      final boolean silent) {
        final long seen = Optional.ofNullable(alert.getCursorId()).orElse(Long.MAX_VALUE);
        final List<NewsModel> fresh = news.stream()
                .filter(item -> item.getId() != null && item.getId() > seen)
                .sorted(Comparator.comparing(NewsModel::getId))
                .toList();
        if (alert.getCursorId() == null) {
            // правило без отметки (до этапа с курсором) - начинаем с текущих новостей, без пачки старых
            news.stream().map(NewsModel::getId).max(Long::compare).ifPresent(alert::setCursorId);
            return 0;
        }
        if (fresh.isEmpty()) {
            return 0;
        }
        alert.setCursorId(fresh.getLast().getId());
        if (silent) {
            return 0;
        }

        final List<NewsModel> matching = fresh.stream()
                .filter(item -> alert.getType() != AlertType.ISSUER_RATING || RATING_SOURCES.contains(item.getSource()))
                .toList();
        // много новостей разом (первая загрузка, сбой ленты) - только последние, без потока сообщений
        final List<NewsModel> toSend = matching.subList(Math.max(0, matching.size() - MAX_PER_ALERT), matching.size());
        int queued = 0;
        for (final NewsModel item : toSend) {
            final String dedupKey = "%s:%d:%d".formatted(alert.getType(), alert.getId(), item.getId());
            if (notificationQueue.enqueue(alert.getUserId(), alert.getId(), alert.getTicker(), dedupKey,
                    AlertTexts.news(alert.getType(), item, bondName, alert.getTicker()))) {
                queued++;
            }
        }
        if (queued > 0) {
            alert.setLastTriggeredAt(LocalDateTime.now(ZoneOffset.UTC));
        }
        return queued;
    }

    private List<NewsModel> bondNews(final String ticker) {
        try {
            return newsUseCase.getByTicker(ticker, PageRequest.of(0, PAGE_SIZE)).getContent();
        } catch (final GeneralNotFoundEntityException e) {
            // облигации нет - правила удалит утренняя проверка
            return List.of();
        }
    }

    private List<NewsModel> keyRateDecisions() {
        return newsUseCase.getFeed(Set.of(NewsSource.CBR_PRESS), PageRequest.of(0, PAGE_SIZE)).getContent().stream()
                .filter(item -> item.getTitle() != null && KEY_RATE_DECISION.matcher(item.getTitle()).find())
                .toList();
    }
}
