package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.model.Pair;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.usecase.GetNeedToUpdateCouponsUseCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetNeedToUpdateCouponsUseCaseImpl implements GetNeedToUpdateCouponsUseCase {
    private final static Pageable ONE_THOUSAND_PAGEABLE = PageRequest.of(0, 1000, Sort.by("id"));

    private final BondRepository bondRepository;

    @Override
    @Transactional
    public List<Pair<String, String>> getUidTickersToUpdate() {
        final Map<String, Pair<String, String>> uidTickers = new HashMap<>();

        Pageable pageable = ONE_THOUSAND_PAGEABLE;
        Slice<Bond> bondSlice;

        do {
            bondSlice = bondRepository.findByNotFixedCouponAndEmptyCoupons(pageable);
            if (bondSlice.hasContent()) {
                bondSlice
                        .stream()
                        .filter(bond -> StringUtils.isNotBlank(bond.getUid()))
                        .filter(bond -> StringUtils.isNotBlank(bond.getTicker()))
                        .forEach(bond -> {
                                    final Pair<String, String> uidTicker = uidTickers.getOrDefault(bond.getUid(),
                                            new Pair<>(bond.getUid(), bond.getTicker()));
                                    uidTickers.put(bond.getUid(), uidTicker);
                                }
                        );
            }

            pageable = bondSlice.getPageable();
        } while (bondSlice.hasNext());

        return uidTickers
                .values()
                .stream().toList();
    }
}
