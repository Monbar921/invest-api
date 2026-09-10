package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondEntityMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondApiUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheRepositoryUseCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ru.invest.api.common.constants.CacheConstants.BOND_REPOSITORY_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.BOND_REPOSITORY_CACHE_NAME;
import static ru.invest.api.tinkoff.supplier.predicates.BondModelCurrencyPredicates.FOREIGN_CURRENCY_PREDICATE;
import static ru.invest.api.tinkoff.supplier.predicates.BondModelCurrencyPredicates.RUBBLE_CURRENCY_PREDICATE;

@Service
@RequiredArgsConstructor
public class TinkoffBondCacheRepositoryUseCaseImpl implements TinkoffBondCacheRepositoryUseCase {

    private static final String ALL_BONDS_KEY = "allBonds";

    @Qualifier(BOND_REPOSITORY_CACHE_MANAGER)
    private final CacheManager bondCacheManager;

    private final BondRepository bondRepository;

    private final TinkoffBondEntityMapper tinkoffBondEntityMapper;

    private final TinkoffBondApiUseCase tinkoffBondApiUseCase;

    @Override
    public Map<String, BondModel> getForeignCurrencyBonds() {
        return filterByCurrencies(
                getCachedAll(), FOREIGN_CURRENCY_PREDICATE
        );
    }

    @Override
    public Map<String, BondModel> getRubbleCurrencyBonds() {
        return filterByCurrencies(
                getCachedAll(), RUBBLE_CURRENCY_PREDICATE
        );
    }

    @Override
    public Map<String, BondModel> getBonds() {
        return getCachedAll();
    }

    private Map<String, BondModel> getCachedAll() {
        final Cache cache = bondCacheManager.getCache(BOND_REPOSITORY_CACHE_NAME);
        if (cache == null) {
            return load(this::loadAllBonds);
        }

        return cache.get(ALL_BONDS_KEY, this::loadAllBonds);
    }


    private Map<String, BondModel> load(final Callable<Map<String, BondModel>> loader) {
        try {
            return loader.call();
        } catch (final Exception e) {
            throw new IllegalStateException("Unable to load bonds", e);
        }
    }

    private Map<String, BondModel> loadAllBonds() {
        final List<Bond> entities = bondRepository.findAll();
        if (CollectionUtils.isEmpty(entities)) {
            return tinkoffBondApiUseCase.getAllBonds();
        }

        return toBondModels(entities);
    }

    private Map<String, BondModel> filterByCurrencies(final Map<String, BondModel> bonds, final Predicate<BondModel> currencyPredicate) {
        if (MapUtils.isEmpty(bonds)) {
            return Collections.emptyMap();
        }

        return bonds
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> currencyPredicate.test(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, BondModel> toBondModels(final List<Bond> entities) {
        return entities.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Bond::getUid, bond -> tinkoffBondEntityMapper.toModel(bond, null)));
    }
}
