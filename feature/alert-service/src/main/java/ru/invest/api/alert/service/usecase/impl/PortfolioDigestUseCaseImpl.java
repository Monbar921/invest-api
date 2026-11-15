package ru.invest.api.alert.service.usecase.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.alert.service.config.AlertProperties;
import ru.invest.api.alert.service.usecase.PortfolioDigestUseCase;
import ru.invest.api.common.entity.BondAlert;
import ru.invest.api.common.entity.News;
import ru.invest.api.common.model.alert.AlertType;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.repository.BondAlertRepository;
import ru.invest.api.common.repository.NewsRepository;
import ru.invest.api.common.repository.NotificationRepository;
import ru.invest.api.portfolio.service.model.PortfolioCalendarModel;
import ru.invest.api.portfolio.service.model.PortfolioGroupModel;
import ru.invest.api.portfolio.service.model.PortfolioModel;
import ru.invest.api.portfolio.service.model.PortfolioSummaryModel;
import ru.invest.api.portfolio.service.usecase.PortfolioCalendarUseCase;
import ru.invest.api.portfolio.service.usecase.PortfolioUseCase;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Дайджест портфеля. Состояние - в правиле: стоимость на прошлом дайджесте (base_value), отпечаток состава портфеля
 * (digest_holdings), последняя просмотренная новость (cursor_id), когда отправлен (last_triggered_at). Пустой дайджест
 * не отправляется и состояние не сдвигает: следующий сравнивается с последним отправленным.
 */
@Slf4j
@Service
@SuppressWarnings("checkstyle:ClassDataAbstractionCoupling")
public class PortfolioDigestUseCaseImpl implements PortfolioDigestUseCase {
    /** Еженедельный дайджест: правило с days = 7; ежедневный - days = 1 */
    public static final int WEEK = 7;
    /** Сколько новостей читать за раз: в тексте всё равно max-news, остальные - числом */
    private static final int NEWS_LIMIT = 100;

    private final AlertProperties properties;
    private final BondAlertRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final NewsRepository newsRepository;
    private final PortfolioUseCase portfolioUseCase;
    private final PortfolioCalendarUseCase calendarUseCase;
    private final NotificationQueue notificationQueue;
    private final TransactionTemplate transaction;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public PortfolioDigestUseCaseImpl(final AlertProperties properties,
                                      final BondAlertRepository alertRepository,
                                      final NotificationRepository notificationRepository,
                                      final NewsRepository newsRepository,
                                      final PortfolioUseCase portfolioUseCase,
                                      final PortfolioCalendarUseCase calendarUseCase,
                                      final NotificationQueue notificationQueue,
                                      final PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.alertRepository = alertRepository;
        this.notificationRepository = notificationRepository;
        this.newsRepository = newsRepository;
        this.portfolioUseCase = portfolioUseCase;
        this.calendarUseCase = calendarUseCase;
        this.notificationQueue = notificationQueue;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public int sendDue(final LocalDate today) {
        final List<BondAlert> alerts = alertRepository.findByEnabledTrueAndTypeIn(Set.of(AlertType.PORTFOLIO_DIGEST));
        int queued = 0;
        for (final BondAlert alert : alerts) {
            if (isWeekly(alert) && today.getDayOfWeek() != properties.getDigest().getWeeklyDay()) {
                continue;
            }
            try {
                if (send(alert.getId(), today)) {
                    queued++;
                }
            } catch (final RuntimeException e) {
                log.error("Portfolio digest failed for alert {}", alert.getId(), e);
            }
        }
        log.info("Portfolio digest: {} rules, {} queued", alerts.size(), queued);
        return queued;
    }

    @Override
    public boolean send(final Long alertId, final LocalDate today) {
        final BondAlert alert = alertRepository.findById(alertId)
                .filter(BondAlert::isEnabled)
                .filter(found -> found.getType() == AlertType.PORTFOLIO_DIGEST)
                .orElse(null);
        if (alert == null) {
            return false;
        }
        final String dedupKey = "%s:%d:%s".formatted(AlertType.PORTFOLIO_DIGEST, alertId, today);
        if (notificationRepository.existsByDedupKey(dedupKey)) {
            return false;
        }
        // портфель, календарь и новости - своими read-only транзакциями, до записи
        final PortfolioModel portfolio = portfolioUseCase.getPortfolio(alert.getUserId(), null);
        if (portfolio.groups().isEmpty()) {
            return false;
        }
        final boolean weekly = isWeekly(alert);
        final BigDecimal value = Optional.ofNullable(portfolio.summary())
                .map(PortfolioSummaryModel::currentAmountInRub)
                .orElse(null);
        final String holdings = holdings(portfolio.groups());
        final BigDecimal previous = alert.getBaseValue();
        final BigDecimal change = value != null && previous != null && holdings.equals(alert.getDigestHoldings())
                ? value.subtract(previous)
                : null;
        final boolean valueChanged = value != null && (previous == null || value.compareTo(previous) != 0);
        final PortfolioCalendarModel calendar = calendarUseCase.getCalendar(alert.getUserId(), today,
                today.plusDays((weekly ? WEEK : 1) - 1L));
        final long latestNews = newsRepository.findMaxId().orElse(0L);
        final List<NewsModel> news = news(alert, portfolio.groups());
        if (!valueChanged && calendar.events().isEmpty() && news.isEmpty()) {
            log.debug("Portfolio digest {} is empty - not sent", alertId);
            return false;
        }
        final LocalDate since = Optional.ofNullable(alert.getLastTriggeredAt())
                .map(time -> time.atOffset(ZoneOffset.UTC).atZoneSameInstant(properties.getZone()).toLocalDate())
                .orElse(null);
        final String text = AlertTexts.digest(weekly, today, value, change, previous, since, calendar, news,
                properties.getDigest().getMaxPayments(), properties.getDigest().getMaxNews());
        return Boolean.TRUE.equals(transaction.execute(status -> alertRepository.findById(alertId)
                .filter(BondAlert::isEnabled)
                .map(current -> {
                    if (!notificationQueue.enqueue(current.getUserId(), current.getId(), null, dedupKey, text)) {
                        return false;
                    }
                    // без стоимости прежняя остаётся: следующее изменение - от последней известной при том же составе
                    if (value != null) {
                        current.setBaseValue(value).setDigestHoldings(holdings);
                    }
                    current.setCursorId(latestNews).setLastTriggeredAt(LocalDateTime.now(ZoneOffset.UTC));
                    return true;
                })
                .orElse(false)));
    }

    /**
     * Новости по облигациям портфеля после прошлого дайджеста; у первого - после включения правила (отметку ставит
     * включение). Правило без отметки - без новостей.
     */
    private List<NewsModel> news(final BondAlert alert, final List<PortfolioGroupModel> groups) {
        final Set<String> isins = groups.stream()
                .map(PortfolioGroupModel::isin)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (alert.getCursorId() == null || isins.isEmpty()) {
            return List.of();
        }
        return newsRepository.findByIsinsAfter(alert.getCursorId(), isins, Limit.of(NEWS_LIMIT)).stream()
                .map(PortfolioDigestUseCaseImpl::toModel)
                .toList();
    }

    private static NewsModel toModel(final News news) {
        return new NewsModel()
                .setId(news.getId())
                .setSource(news.getSource())
                .setTitle(news.getTitle())
                .setUrl(news.getUrl())
                .setPublishedAt(news.getPublishedAt());
    }

    private static boolean isWeekly(final BondAlert alert) {
        return Integer.valueOf(WEEK).equals(alert.getDays());
    }

    /**
     * Отпечаток состава: тикер и количество непроданных облигаций. Тот же состав - изменение стоимости только от цен и
     * курсов; изменился - в изменении были бы покупки и продажи.
     */
    static String holdings(final List<PortfolioGroupModel> groups) {
        final String composition = groups.stream()
                .sorted(Comparator.comparing(PortfolioGroupModel::ticker))
                .map(group -> group.ticker() + "=" + group.quantity())
                .collect(Collectors.joining(","));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(composition.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
