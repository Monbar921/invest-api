package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Price;
import ru.invest.api.common.model.PriceModel;
import ru.invest.api.common.repository.PriceRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffPriceEntityMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffPriceUseCase;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TinkoffPriceUseCaseImpl implements TinkoffPriceUseCase {
    private final PriceRepository priceRepository;
    private final TinkoffPriceEntityMapper tinkoffPriceEntityMapper;

    @Override
    @Transactional
    public void saveCurrentPrices(final Map<String, PriceModel> prices) {
        if (MapUtils.isEmpty(prices)) {
            return;
        }

        final List<Price> existingPrices = priceRepository.findByUidIn(prices.keySet())
                .stream()
                .filter(Objects::nonNull)
                .toList();

        existingPrices.forEach(price -> tinkoffPriceEntityMapper.updateCurrentPrice(prices.get(price.getUid()), price));

        final Set<String> missingUids = new HashSet<>(prices.keySet());
        existingPrices.forEach(price -> missingUids.remove(price.getUid()));
        if (!missingUids.isEmpty()) {
            log.warn("Price is not found for {} bonds, sync bonds first. Uids: {}", missingUids.size(), missingUids);
        }

        priceRepository.saveAll(existingPrices);
    }
}
