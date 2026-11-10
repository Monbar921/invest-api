package ru.invest.api.news.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.BondIssuer;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.news.IssuerPageModel;
import ru.invest.api.common.repository.BondIssuerRepository;
import ru.invest.api.news.supplier.usecase.IssuerUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IssuerUseCaseImpl implements IssuerUseCase {
    private static final String ISSUER_NOT_FOUND_MESSAGE = "Issuer not found for id %s";
    private static final Comparator<BondModel> BY_MATURITY = Comparator
            .comparing(BondModel::getMaturityDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(BondModel::getTicker, Comparator.nullsLast(Comparator.naturalOrder()));

    private final BondIssuerRepository bondIssuerRepository;
    private final TinkoffBondCacheRepositoryUseCase tinkoffBondCacheRepositoryUseCase;

    @Override
    @Transactional(readOnly = true)
    public IssuerPageModel getIssuer(final Long id) {
        final List<BondIssuer> rows = bondIssuerRepository.findByEmitterId(id);
        if (rows.isEmpty()) {
            throw new GeneralNotFoundEntityException(ExceptionErrorCode.ISSUER_NOT_FOUND, ISSUER_NOT_FOUND_MESSAGE.formatted(id));
        }

        final Set<String> isins = rows.stream().map(BondIssuer::getIsin).filter(Objects::nonNull).collect(Collectors.toSet());
        return toModel(id, rows).setBonds(bondsByIsin(isins));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IssuerPageModel> findByIsin(final String isin) {
        if (isin == null) {
            return Optional.empty();
        }
        return bondIssuerRepository.findByIsin(isin)
                .filter(issuer -> issuer.getEmitterId() != null)
                .map(issuer -> toModel(issuer.getEmitterId(), List.of(issuer)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getIssuerIds() {
        return bondIssuerRepository.findEmitterIdsWithBonds();
    }

    // у выпусков одного эмитента название и ИНН одинаковые; если у какого-то их нет - берётся первый заполненный
    private IssuerPageModel toModel(final Long id, final Collection<BondIssuer> rows) {
        final String title = rows.stream().map(BondIssuer::getTitle).filter(Objects::nonNull).findFirst().orElse(null);
        return new IssuerPageModel()
                .setId(id)
                .setTitle(title)
                .setShortTitle(IssuerTitles.shortTitle(title))
                .setInn(rows.stream().map(BondIssuer::getInn).filter(Objects::nonNull).findFirst().orElse(null))
                .setBonds(List.of());
    }

    private List<BondModel> bondsByIsin(final Set<String> isins) {
        final Map<String, BondModel> cachedBonds = tinkoffBondCacheRepositoryUseCase.getBonds();
        if (cachedBonds == null || isins.isEmpty()) {
            return List.of();
        }
        return cachedBonds.values().stream()
                .filter(Objects::nonNull)
                .filter(bond -> isins.contains(bond.getIsin()))
                .sorted(BY_MATURITY)
                .toList();
    }
}
