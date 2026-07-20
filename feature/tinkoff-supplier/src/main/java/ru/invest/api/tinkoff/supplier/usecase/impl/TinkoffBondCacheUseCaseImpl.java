package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondApiUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondCacheUseCase;

import javax.cache.CacheManager;
import java.util.List;
import java.util.Map;

import static ru.invest.api.common.constants.CacheConstants.BOND_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.BOND_CACHE_NAME;
import static ru.invest.api.tinkoff.supplier.predicates.BondCurrencyPredicates.FOREIGN_CURRENCIES;
import static ru.invest.api.tinkoff.supplier.predicates.BondCurrencyPredicates.RU_CURRENCIES;

@Component
@RequiredArgsConstructor
public class TinkoffBondCacheUseCaseImpl implements TinkoffBondCacheUseCase {
    @Qualifier(BOND_CACHE_MANAGER)
    private final CacheManager bondCacheManager;

    private final BondRepository bondRepository;

    private final TinkoffBondApiUseCase tinkoffBondApiUseCase;

    @Override
    public Map<String, BondModel> getForeignCurrencyBonds() {
        return getBonds(FOREIGN_CURRENCIES, bondParameters);
    }

    @Override
    public Map<String, BondModel> getRubbleCurrencyBonds() {
        return getBonds(RU_CURRENCIES, bondParameters);
    }

    @Override
    @Cacheable(cacheNames = BOND_CACHE_NAME, cacheManager = BOND_CACHE_MANAGER)
    public Map<String, BondModel> getBonds() {
        final List<Bond> entities = bondRepository.findAll();
        if (CollectionUtils.isEmpty(entities)) {
            return tinkoffBondApiUseCase.getAllBonds();
        }

        return entities;
    }
}
