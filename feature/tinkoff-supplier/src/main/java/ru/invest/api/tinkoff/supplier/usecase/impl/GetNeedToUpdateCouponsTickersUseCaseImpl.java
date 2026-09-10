package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.usecase.GetNeedToUpdateCouponsTickersUseCase;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetNeedToUpdateCouponsTickersUseCaseImpl implements GetNeedToUpdateCouponsTickersUseCase {
    private final static Pageable ONE_THOUSAND_PAGEABLE = PageRequest.of(0, 1000, Sort.by("id"));

    private final BondRepository bondRepository;

    @Override
    @Transactional
    public Set<String> getTickersToUpdate() {
        final Set<String> tickers = new HashSet<>();

        Pageable pageable = ONE_THOUSAND_PAGEABLE;
        Slice<Bond> bondSlice;

        do {
            bondSlice = bondRepository.findByNotFixedCouponAndEmptyCoupons(pageable);
            if (bondSlice.hasContent()) {
                final Set<String> sliceTickers = getTickers(bondSlice.getContent());
                if (CollectionUtils.isNotEmpty(sliceTickers)) {
                    tickers.addAll(sliceTickers);
                }
            }

            pageable = bondSlice.getPageable();
        } while (bondSlice.hasNext());

        return tickers;
    }

    private Set<String> getTickers(final List<Bond> bonds) {
        if (CollectionUtils.isEmpty(bonds)) {
            return Collections.emptySet();
        }

        return bonds
                .stream()
                .filter(Objects::nonNull)
                .map(Bond::getTicker)
                .collect(Collectors.toSet());
    }
}
