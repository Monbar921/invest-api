package ru.invest.api.news.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.invest.api.common.entity.Audit;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.BondIssuer;
import ru.invest.api.common.entity.CreditRating;
import ru.invest.api.common.mapper.AuditMapper;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.model.rating.RatingAgency;
import ru.invest.api.common.model.rating.RatingScope;
import ru.invest.api.common.repository.BondIssuerRepository;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.CreditRatingRepository;
import ru.invest.api.news.supplier.config.NewsProperties;
import ru.invest.api.news.supplier.usecase.CreditRatingSyncUseCase;
import ru.invest.api.news.supplier.usecase.ExternalNewsUseCase;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditRatingSyncUseCaseImpl implements CreditRatingSyncUseCase {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final int TEXT_LENGTH = 1000;

    private final CreditRatingRepository creditRatingRepository;
    private final BondIssuerRepository bondIssuerRepository;
    private final BondRepository bondRepository;
    private final AuditMapper auditMapper;
    private final TransactionTemplate transactionTemplate;
    private final ExternalNewsUseCase externalNewsUseCase;
    private final NewsProperties newsProperties;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public int loadHistory(final AuditModel audit) {
        final NewsProperties.Ratings settings = newsProperties.getRatings();
        final LocalDate since = dateTimeMapper.getNow().toLocalDate().minus(settings.getMaxAge());
        final List<NewsModel> releases = externalNewsUseCase.getRatingHistory(since, settings.getHistoryMaxPages(),
                settings.getHistoryPagePause());
        log.info("Credit rating history since {}: {} releases", since, releases.size());
        return apply(releases, audit);
    }

    @Override
    public int apply(final List<NewsModel> releases, final AuditModel audit) {
        final List<NewsModel> ratingReleases = releases.stream()
                .filter(Objects::nonNull)
                .filter(release -> release.getSource() == NewsSource.ACRA || release.getSource() == NewsSource.NKR)
                .filter(release -> release.getPublishedAt() != null)
                .collect(Collectors.toCollection(ArrayList::new));
        if (ratingReleases.isEmpty()) {
            return 0;
        }
        // списки агентств - новые сверху; применяем от старых к новым, чтобы последнее действие победило
        Collections.reverse(ratingReleases);
        ratingReleases.sort(Comparator.comparing(NewsModel::getPublishedAt));

        final IssuerMatcher matcher = new IssuerMatcher(bondIssuerRepository.findAll());
        final Map<String, CreditRating> current = new HashMap<>();
        creditRatingRepository.findAll().forEach(rating -> current.put(key(rating), rating));
        final Map<String, CreditRating> changed = new LinkedHashMap<>();
        final Audit updated = auditMapper.toEntity(audit);

        for (final NewsModel release : ratingReleases) {
            final RatingAgency agency = release.getSource() == NewsSource.ACRA ? RatingAgency.ACRA : RatingAgency.NKR;
            final LocalDate date = release.getPublishedAt().atOffset(ZoneOffset.UTC).atZoneSameInstant(MOSCOW).toLocalDate();
            for (final CreditRatingParser.Action action : CreditRatingParser.parse(agency, release.getTitle())) {
                subjects(action, release, matcher).forEach(subject -> {
                    final CreditRating rating = new CreditRating()
                            .setAgency(agency)
                            .setScope(action.scope())
                            .setEmitterId(subject.emitterId())
                            .setIsin(subject.isin());
                    final CreditRating existing = current.get(key(rating));
                    // действие старше сохранённого (повторная загрузка истории) ничего не меняет
                    if (existing != null && existing.getActionDate().isAfter(date)) {
                        return;
                    }
                    final CreditRating target = existing == null ? rating : existing;
                    fill(target, action, release, date, updated);
                    current.put(key(target), target);
                    changed.put(key(target), target);
                });
            }
        }

        transactionTemplate.executeWithoutResult(status -> creditRatingRepository.saveAll(changed.values()));
        log.info("Credit ratings: {} releases, {} ratings saved", ratingReleases.size(), changed.size());
        return changed.size();
    }

    private void fill(final CreditRating target, final CreditRatingParser.Action action, final NewsModel release,
                      final LocalDate date, final Audit updated) {
        final CreditRatingParser.Rating rating = action.rating();
        target.setRating(rating == null ? null : rating.rating())
                .setGrade(rating == null ? null : rating.grade())
                .setScore(rating == null ? null : rating.score())
                .setOutlook(action.outlook())
                .setWithdrawn(rating == null)
                .setActionDate(date)
                .setReleaseId(release.getExternalId())
                .setTitle(StringUtils.left(release.getTitle(), TEXT_LENGTH))
                .setUrl(StringUtils.left(release.getUrl(), TEXT_LENGTH))
                .setUpdated(updated);
    }

    /**
     * Кому относится действие. Эмитент - только если в релизе нашёлся ровно один эмитент из системы: иначе неясно,
     * кому из них рейтинг (группа и её дочерняя компания). Выпуск без ISIN - выпуски этого эмитента, в названии
     * которых есть номер серии.
     */
    private List<Subject> subjects(final CreditRatingParser.Action action, final NewsModel release, final IssuerMatcher matcher) {
        if (action.isin() != null) {
            return List.of(new Subject(null, action.isin()));
        }
        final String text = release.getTitle() + " " + StringUtils.defaultString(release.getBodyForMatching());
        final Set<Long> emitters = matcher.emitters(text, IssuerMatcher.isins(text));
        if (emitters.size() != 1) {
            return List.of();
        }
        final Long emitterId = emitters.iterator().next();
        if (action.scope() == RatingScope.ISSUER) {
            return List.of(new Subject(emitterId, null));
        }
        return issuesBySeries(emitterId, action.series());
    }

    private List<Subject> issuesBySeries(final Long emitterId, final String series) {
        if (StringUtils.isBlank(series)) {
            return List.of();
        }
        final Set<String> isins = bondIssuerRepository.findByEmitterId(emitterId).stream()
                .map(BondIssuer::getIsin)
                .collect(Collectors.toSet());
        if (isins.isEmpty()) {
            return List.of();
        }
        final String normalizedSeries = CreditRatingParser.normalize(series);
        return bondRepository.findByIsinIn(isins).stream()
                .filter(bond -> bond.getName() != null && containsWord(CreditRatingParser.normalize(bond.getName()), normalizedSeries))
                .map(Bond::getIsin)
                .distinct()
                .map(isin -> new Subject(null, isin))
                .toList();
    }

    private boolean containsWord(final String text, final String word) {
        int index = text.indexOf(word);
        while (index >= 0) {
            final int end = index + word.length();
            final boolean startOk = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
            final boolean endOk = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            index = text.indexOf(word, index + 1);
        }
        return false;
    }

    private static String key(final CreditRating rating) {
        return rating.getAgency() + ":" + rating.getScope() + ":"
                + (rating.getScope() == RatingScope.ISSUER ? rating.getEmitterId() : rating.getIsin());
    }

    private record Subject(Long emitterId, String isin) {
    }
}
