package ru.invest.api.telegram.bot.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.LinkPreviewOptions;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.invest.api.bond.supplier.usecase.BondUseCase;
import ru.invest.api.common.entity.ChannelPost;
import ru.invest.api.common.mapper.BondParametersMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.channel.ChannelPostKind;
import ru.invest.api.common.model.channel.ChannelPostStatus;
import ru.invest.api.common.model.news.KeyRateDecision;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.model.parameters.BondParametersRequestModel;
import ru.invest.api.common.model.parameters.BondSortField;
import ru.invest.api.common.model.parameters.BondSortModel;
import ru.invest.api.common.model.parameters.BondSortOrder;
import ru.invest.api.common.model.parameters.RatingRangeModel;
import ru.invest.api.common.model.parameters.ValueRangeModel;
import ru.invest.api.common.repository.ChannelPostRepository;
import ru.invest.api.news.supplier.usecase.NewsUseCase;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "ru.invest.api.telegram", name = "enabled", havingValue = "true")
public class ChannelUseCaseImpl implements ChannelUseCase {
    /** Сколько последних новостей источника просматривать: лента грузится раз в 15 минут, больше не набирается */
    private static final int NEWS_PAGE = 100;
    /** Сколько облигаций группы брать до фильтра по валюте */
    private static final int BONDS_PAGE = 50;
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);
    private static final int BAD_REQUEST = 400;
    private static final int FORBIDDEN = 403;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final String RUB = "rub";

    private final ChannelProperties properties;
    private final ChannelPostRepository repository;
    private final NewsUseCase newsUseCase;
    private final BondUseCase bondUseCase;
    private final BondParametersMapper bondParametersMapper;
    private final TelegramClient telegramClient;
    private final TransactionTemplate transaction;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public ChannelUseCaseImpl(final ChannelProperties properties,
                              final ChannelPostRepository repository,
                              final NewsUseCase newsUseCase,
                              final BondUseCase bondUseCase,
                              final BondParametersMapper bondParametersMapper,
                              final TelegramClient telegramClient,
                              final PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.repository = repository;
        this.newsUseCase = newsUseCase;
        this.bondUseCase = bondUseCase;
        this.bondParametersMapper = bondParametersMapper;
        this.telegramClient = telegramClient;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // ---------- сбор ----------

    @Override
    public int collectNews(final Instant now) {
        if (!properties.isEnabled()) {
            return 0;
        }
        final LocalDateTime since = utc(now).minus(properties.getMaxAge());
        int queued = 0;
        if (properties.isRatingActions()) {
            for (final NewsModel news : recent(Set.of(NewsSource.ACRA, NewsSource.NKR), since)) {
                queued += ratingAction(news, now) ? 1 : 0;
            }
        }
        if (properties.isKeyRate()) {
            for (final NewsModel news : recent(Set.of(NewsSource.CBR_PRESS), since)) {
                if (KeyRateDecision.matches(news.getTitle())
                        && enqueue(ChannelPostKind.KEY_RATE, "KEY_RATE:" + news.getId(),
                        ChannelTexts.keyRate(news.getTitle(), date(news), news.getUrl(), properties.page("/collections/floaters")),
                        news.getPublishedAt(), now)) {
                    queued++;
                }
            }
        }
        if (queued > 0) {
            log.info("Telegram channel: {} posts queued from news", queued);
        }
        return queued;
    }

    /**
     * Пост - только о рейтинговом действии по эмитенту или выпуску из системы: у поста должна быть страница продукта.
     */
    private boolean ratingAction(final NewsModel news, final Instant now) {
        final Optional<RatingActionClassifier.Change> change = RatingActionClassifier.classify(news.getSource(),
                news.getTitle());
        if (change.isEmpty()) {
            return false;
        }
        final Optional<String> page = news.getEmitterIds().stream().min(Long::compare)
                .map(id -> properties.page("/issuers/" + id))
                .or(() -> news.getBonds().stream().findFirst().map(bond -> properties.page("/bonds/" + bond.getTicker())));
        if (page.isEmpty()) {
            return false;
        }
        final List<String> bonds = news.getBonds().stream()
                .map(bond -> "%s (%s)".formatted(bond.getName(), bond.getTicker()))
                .toList();
        return enqueue(ChannelPostKind.RATING_ACTION, "RATING_ACTION:" + news.getId(),
                ChannelTexts.ratingAction(change.get(), news.getTitle(), news.getIssuers(), bonds, news.getSource(),
                        date(news), news.getUrl(), page.get()),
                news.getPublishedAt(), now);
    }

    private List<NewsModel> recent(final Set<NewsSource> sources, final LocalDateTime since) {
        return newsUseCase.getFeed(sources, PageRequest.of(0, NEWS_PAGE)).getContent().stream()
                .filter(news -> news.getId() != null && news.getPublishedAt() != null
                        && !news.getPublishedAt().isBefore(since))
                // старые первыми: в канале события идут по порядку
                .sorted(Comparator.comparing(NewsModel::getPublishedAt).thenComparing(NewsModel::getId))
                .toList();
    }

    @Override
    public boolean collectWeeklyTop(final Instant now) {
        if (!properties.isEnabled() || !properties.getWeeklyTop().isEnabled()) {
            return false;
        }
        final LocalDate today = now.atZone(properties.getZone()).toLocalDate();
        final String dedupKey = "WEEKLY_TOP:%d-W%02d".formatted(today.get(IsoFields.WEEK_BASED_YEAR),
                today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        if (repository.existsByDedupKey(dedupKey)) {
            return false;
        }
        final Map<String, List<BondModel>> groups = new LinkedHashMap<>();
        properties.getWeeklyTop().getGroups().forEach(group -> {
            final List<BondModel> bonds = topOfGroup(group);
            if (!bonds.isEmpty()) {
                groups.put(group.getTitle(), bonds);
            }
        });
        if (groups.isEmpty()) {
            log.info("Telegram channel: weekly top is empty - not posted");
            return false;
        }
        final boolean queued = enqueue(ChannelPostKind.WEEKLY_TOP, dedupKey,
                ChannelTexts.weeklyTop(today, groups, properties.getWeeklyTop(),
                        ticker -> properties.page("/bonds/" + ticker), properties.page("/bonds")),
                utc(now), now);
        if (queued) {
            log.info("Telegram channel: weekly top queued, {} groups", groups.size());
        }
        return queued;
    }

    private List<BondModel> topOfGroup(final ChannelProperties.Group group) {
        final ChannelProperties.WeeklyTop settings = properties.getWeeklyTop();
        final BondParametersRequestModel parameters = new BondParametersRequestModel();
        // у флоатера доходность к погашению считается по текущему купону - со ступенчатыми фиксированными не сравнить
        parameters.setIsFixedCoupon(true);
        parameters.setCreditRating(new RatingRangeModel().setMin(group.getMin()).setMax(group.getMax()));
        parameters.setYearsToMaturity(new ValueRangeModel().setMin(settings.getMinYearsToMaturity()));
        if (settings.getMinTurnoverRub() != null) {
            parameters.setAvgDailyTurnover(new ValueRangeModel().setMin(settings.getMinTurnoverRub()));
        }
        parameters.setBondSorts(List.of(new BondSortModel()
                .setSortField(BondSortField.YIELD_TO_MATURITY)
                .setSortOrder(BondSortOrder.DESC)));
        return bondUseCase.getAllPage(bondParametersMapper.toModel(parameters), PageRequest.of(0, BONDS_PAGE))
                .getContent().stream()
                .filter(bond -> RUB.equalsIgnoreCase(bond.getCurrency()))
                .filter(bond -> bond.getCoupon() != null && bond.getCoupon().getYieldToMaturity() != null
                        && bond.getCoupon().getYieldToMaturity().signum() > 0)
                .limit(settings.getPerGroup())
                .toList();
    }

    /**
     * @param eventAt когда произошло событие (UTC): пост актуален max-age с этого момента
     */
    private boolean enqueue(final ChannelPostKind kind, final String dedupKey, final String text,
                            final LocalDateTime eventAt, final Instant now) {
        if (repository.existsByDedupKey(dedupKey)) {
            return false;
        }
        final LocalDateTime created = utc(now);
        repository.save(new ChannelPost()
                .setKind(kind)
                .setDedupKey(dedupKey)
                .setText(text)
                .setStatus(ChannelPostStatus.PENDING)
                .setAttempts(0)
                .setNextAttemptAt(created)
                .setExpiresAt(Objects.requireNonNullElse(eventAt, created).plus(properties.getMaxAge()))
                .setCreatedAt(created));
        return true;
    }

    // ---------- публикация ----------

    @Override
    public boolean publish(final Instant now) {
        if (!properties.isEnabled()) {
            return false;
        }
        final LocalDateTime utcNow = utc(now);
        final Integer expired = transaction.execute(status -> repository.expire(ChannelPostStatus.PENDING,
                ChannelPostStatus.SKIPPED, utcNow, "Не опубликован за %d ч: лимит постов, ночь или нет связи"
                        .formatted(properties.getMaxAge().toHours())));
        if (expired != null && expired > 0) {
            log.info("Telegram channel: {} posts expired", expired);
        }

        final ZonedDateTime local = now.atZone(properties.getZone());
        final LocalTime time = local.toLocalTime();
        if (time.isBefore(properties.getPostingFrom()) || !time.isBefore(properties.getPostingTo())) {
            return false;
        }
        final LocalDateTime dayStart = utc(local.toLocalDate().atStartOfDay(properties.getZone()).toInstant());
        if (repository.countByStatusAndSentAtGreaterThanEqual(ChannelPostStatus.SENT, dayStart)
                >= properties.getMaxPostsPerDay()) {
            return false;
        }
        final boolean pause = repository.findFirstByStatusOrderBySentAtDesc(ChannelPostStatus.SENT)
                .map(ChannelPost::getSentAt)
                .filter(sentAt -> sentAt.plus(properties.getMinInterval()).isAfter(utcNow))
                .isPresent();
        if (pause) {
            return false;
        }
        return repository.findFirstByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAscIdAsc(
                        ChannelPostStatus.PENDING, utcNow)
                .map(post -> send(post, utcNow))
                .orElse(false);
    }

    private boolean send(final ChannelPost post, final LocalDateTime now) {
        post.setAttempts(post.getAttempts() + 1);
        try {
            final Message message = telegramClient.execute(SendMessage.builder()
                    .chatId(properties.getChatId())
                    .text(post.getText())
                    // ссылок в посте несколько: карточка-превью первой из них только мешает
                    .linkPreviewOptions(LinkPreviewOptions.builder().isDisabled(true).build())
                    .build());
            post.setStatus(ChannelPostStatus.SENT)
                    .setSentAt(now)
                    .setMessageId(message == null ? null : message.getMessageId())
                    .setLastError(null);
            log.info("Telegram channel: post {} ({}) published", post.getId(), post.getKind());
        } catch (final TelegramApiRequestException e) {
            requestFailed(post, e, now);
        } catch (final TelegramApiException e) {
            // сеть, прокси, блокировка: повторим, пока пост актуален
            post.setLastError(e.getMessage()).setNextAttemptAt(now.plus(RETRY_DELAY));
            log.warn("Telegram channel: post {} not published, will retry: {}", post.getId(), e.getMessage());
        }
        repository.save(post);
        return post.getStatus() == ChannelPostStatus.SENT;
    }

    private void requestFailed(final ChannelPost post, final TelegramApiRequestException e, final LocalDateTime now) {
        final Integer code = e.getErrorCode();
        post.setLastError(e.getMessage());
        if (code != null && (code == BAD_REQUEST || code == FORBIDDEN)) {
            // бот не администратор канала, канал не найден, текст не принят - повтор не поможет
            post.setStatus(ChannelPostStatus.FAILED);
            log.error("Telegram channel: post {} rejected by Telegram ({}): {}", post.getId(), code, e.getMessage());
            return;
        }
        Duration delay = RETRY_DELAY;
        if (code != null && code == TOO_MANY_REQUESTS && e.getParameters() != null
                && e.getParameters().getRetryAfter() != null) {
            final Duration retryAfter = Duration.ofSeconds(e.getParameters().getRetryAfter());
            delay = retryAfter.compareTo(delay) > 0 ? retryAfter : delay;
        }
        post.setNextAttemptAt(now.plus(delay));
        log.warn("Telegram channel: post {} not published ({}), will retry: {}", post.getId(), code, e.getMessage());
    }

    @Override
    public int cleanUp(final Instant now) {
        return Objects.requireNonNullElse(transaction.execute(status -> repository.deleteFinishedBefore(
                ChannelPostStatus.PENDING, utc(now).minus(properties.getRetention()))), 0);
    }

    private LocalDate date(final NewsModel news) {
        return news.getPublishedAt() == null ? null
                : news.getPublishedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(properties.getZone()).toLocalDate();
    }

    private static LocalDateTime utc(final Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
