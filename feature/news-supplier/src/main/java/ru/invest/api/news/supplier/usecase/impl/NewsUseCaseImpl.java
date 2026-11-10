package ru.invest.api.news.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.entity.BondIssuer;
import ru.invest.api.common.entity.News;
import ru.invest.api.common.entity.NewsLink;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.news.NewsBondModel;
import ru.invest.api.common.model.news.NewsModel;
import ru.invest.api.common.model.news.NewsSource;
import ru.invest.api.common.repository.BondIssuerRepository;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.common.repository.NewsRepository;
import ru.invest.api.news.supplier.usecase.NewsUseCase;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NewsUseCaseImpl implements NewsUseCase {
    private static final String BOND_NOT_FOUND_MESSAGE = "Bond not found for ticker %s";
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    // в сообщениях о приостановке торгов бывают десятки выпусков - в ленте хватит нескольких
    private static final int MAX_BONDS_PER_NEWS = 5;
    private final NewsRepository newsRepository;
    private final BondRepository bondRepository;
    private final BondIssuerRepository bondIssuerRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<NewsModel> getFeed(final Set<NewsSource> sources, final Pageable pageable) {
        final Set<NewsSource> selected = CollectionUtils.isEmpty(sources) ? EnumSet.allOf(NewsSource.class) : sources;
        return toModels(newsRepository.findBySourceIn(selected, newestFirst(pageable)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NewsModel> getByTicker(final String ticker, final Pageable pageable) {
        final Bond bond = bondRepository.findByTicker(ticker)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.BOND_NOT_FOUND,
                        BOND_NOT_FOUND_MESSAGE.formatted(ticker)));
        if (bond.getIsin() == null) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return toModels(newsRepository.findByIsin(bond.getIsin(), newestFirst(pageable)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NewsModel> getByIssuer(final Long emitterId, final Pageable pageable) {
        return toModels(newsRepository.findByEmitterId(emitterId, newestFirst(pageable)));
    }

    private Pageable newestFirst(final Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), NEWEST_FIRST);
    }

    private Page<NewsModel> toModels(final Page<News> page) {
        if (page.isEmpty()) {
            return page.map(news -> new NewsModel());
        }

        final Map<Long, List<NewsLink>> links = newsRepository.findWithLinksByIdIn(page.map(News::getId).toList())
                .stream()
                .collect(Collectors.toMap(News::getId, News::getLinks));
        final Set<String> isins = links.values().stream()
                .flatMap(List::stream)
                .map(NewsLink::getIsin)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        final Set<Long> emitterIds = links.values().stream()
                .flatMap(List::stream)
                .map(NewsLink::getEmitterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        final Map<String, Bond> bondsByIsin = isins.isEmpty() ? Map.of() : bondRepository.findByIsinIn(isins)
                .stream()
                .collect(Collectors.toMap(Bond::getIsin, Function.identity(), (first, second) -> first));
        final Map<Long, String> issuerTitles = emitterIds.isEmpty() ? Map.of() : bondIssuerRepository.findByEmitterIdIn(emitterIds)
                .stream()
                .filter(issuer -> issuer.getTitle() != null)
                .collect(Collectors.toMap(BondIssuer::getEmitterId, issuer -> IssuerTitles.shortTitle(issuer.getTitle()),
                        (first, second) -> first));

        return page.map(news -> {
            final List<NewsLink> newsLinks = links.getOrDefault(news.getId(), List.of());
            return new NewsModel()
                    .setId(news.getId())
                    .setSource(news.getSource())
                    .setExternalId(news.getExternalId())
                    .setTitle(news.getTitle())
                    .setSummary(news.getSummary())
                    .setUrl(news.getUrl())
                    .setPublishedAt(news.getPublishedAt())
                    .setBonds(newsLinks.stream()
                            .map(NewsLink::getIsin)
                            .filter(Objects::nonNull)
                            .map(bondsByIsin::get)
                            .filter(Objects::nonNull)
                            .distinct()
                            .limit(MAX_BONDS_PER_NEWS)
                            .map(bond -> new NewsBondModel(bond.getTicker(), bond.getName()))
                            .toList())
                    .setIssuers(newsLinks.stream()
                            .map(NewsLink::getEmitterId)
                            .filter(Objects::nonNull)
                            .map(issuerTitles::get)
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList());
        });
    }
}
