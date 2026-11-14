package ru.invest.api.news.supplier.screener;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.invest.api.common.entity.BondIssuer;
import ru.invest.api.common.entity.CreditRating;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.rating.CreditRatingModel;
import ru.invest.api.common.model.rating.RatingAgency;
import ru.invest.api.common.model.rating.RatingScope;
import ru.invest.api.common.repository.BondIssuerRepository;
import ru.invest.api.common.repository.CreditRatingRepository;
import ru.invest.api.common.usecase.BondScreenerEnricher;
import ru.invest.api.news.supplier.config.NewsProperties;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Рейтинг облигации: у каждого агентства - рейтинг выпуска, а без него - рейтинг эмитента; для сравнения берётся
 * самый низкий из агентств (осторожная оценка), при равенстве - самый свежий.
 */
@Service
@RequiredArgsConstructor
public class CreditRatingEnricher implements BondScreenerEnricher {
    // пустой список в «in» даёт некорректный SQL; эмитента с таким id нет
    private static final Long NO_EMITTER = -1L;
    private static final Comparator<CreditRatingModel> LOWEST_FIRST = Comparator
            .comparingInt(CreditRatingModel::getScore)
            .thenComparing(CreditRatingModel::getActionDate, Comparator.reverseOrder());

    private final CreditRatingRepository creditRatingRepository;
    private final BondIssuerRepository bondIssuerRepository;
    private final NewsProperties newsProperties;
    private final DateTimeMapper dateTimeMapper;

    @Override
    public void enrich(final Collection<BondModel> bonds, final boolean detailed) {
        final Set<String> isins = bonds.stream()
                .filter(Objects::nonNull)
                .map(BondModel::getIsin)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        if (isins.isEmpty()) {
            return;
        }
        final Map<String, Long> emitterByIsin = bondIssuerRepository.findByIsinIn(isins).stream()
                .filter(issuer -> issuer.getEmitterId() != null)
                .collect(Collectors.toMap(BondIssuer::getIsin, BondIssuer::getEmitterId, (first, second) -> first));
        final Set<Long> emitterIds = new HashSet<>(emitterByIsin.values());
        emitterIds.add(NO_EMITTER);

        final LocalDate since = dateTimeMapper.getNow().toLocalDate().minus(newsProperties.getRatings().getMaxAge());
        final Map<String, CreditRating> issueRatings = new HashMap<>();
        final Map<String, CreditRating> issuerRatings = new HashMap<>();
        creditRatingRepository.findActive(isins, emitterIds, since).forEach(rating -> {
            if (rating.getScope() == RatingScope.ISSUE) {
                issueRatings.put(rating.getAgency() + ":" + rating.getIsin(), rating);
            } else {
                issuerRatings.put(rating.getAgency() + ":" + rating.getEmitterId(), rating);
            }
        });
        if (issueRatings.isEmpty() && issuerRatings.isEmpty()) {
            return;
        }

        bonds.stream()
                .filter(Objects::nonNull)
                .filter(bond -> StringUtils.isNotBlank(bond.getIsin()))
                .forEach(bond -> {
                    final Map<RatingAgency, CreditRatingModel> byAgency = new EnumMap<>(RatingAgency.class);
                    for (final RatingAgency agency : RatingAgency.values()) {
                        CreditRating rating = issueRatings.get(agency + ":" + bond.getIsin());
                        if (rating == null) {
                            rating = issuerRatings.get(agency + ":" + emitterByIsin.get(bond.getIsin()));
                        }
                        if (rating != null) {
                            byAgency.put(agency, toModel(rating));
                        }
                    }
                    if (byAgency.isEmpty()) {
                        return;
                    }
                    final List<CreditRatingModel> ratings = new ArrayList<>(byAgency.values());
                    bond.setCreditRating(ratings.stream().min(LOWEST_FIRST).orElseThrow());
                    if (detailed) {
                        bond.setCreditRatings(ratings);
                    }
                });
    }

    private CreditRatingModel toModel(final CreditRating rating) {
        return new CreditRatingModel()
                .setAgency(rating.getAgency())
                .setScope(rating.getScope())
                .setRating(rating.getRating())
                .setGrade(rating.getGrade())
                .setScore(rating.getScore())
                .setOutlook(rating.getOutlook())
                .setActionDate(rating.getActionDate())
                .setUrl(rating.getUrl());
    }
}
