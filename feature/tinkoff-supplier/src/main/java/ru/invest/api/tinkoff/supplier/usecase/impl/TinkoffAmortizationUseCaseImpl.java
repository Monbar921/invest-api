package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.ShortProductModel;
import ru.invest.api.common.repository.AmortizationRepository;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffAmortizationMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffAmortizationUseCase;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TinkoffAmortizationUseCaseImpl implements TinkoffAmortizationUseCase {
    private final BondRepository bondRepository;
    private final AmortizationRepository amortizationRepository;
    private final TinkoffAmortizationMapper tinkoffAmortizationMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ShortProductModel> getAmortizingBonds() {
        return bondRepository.findAmortizing()
                .stream()
                .filter(bond -> StringUtils.isNotBlank(bond.getUid()))
                .filter(bond -> StringUtils.isNotBlank(bond.getTicker()))
                .map(bond -> new ShortProductModel()
                        .setUid(bond.getUid())
                        .setTicker(bond.getTicker()))
                .toList();
    }

    @Override
    @Transactional
    public void save(final Map<String, List<AmortizationModel>> amortizations) {
        if (MapUtils.isEmpty(amortizations)) {
            return;
        }

        final List<Bond> bonds = bondRepository.findWithAmortizationsByUidIn(amortizations.keySet());
        bonds.forEach(bond -> tinkoffAmortizationMapper.replace(bond, amortizations.get(bond.getUid())));

        bondRepository.saveAll(bonds);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<AmortizationModel>> findByUids(final Set<String> uids) {
        if (CollectionUtils.isEmpty(uids)) {
            return Collections.emptyMap();
        }

        return tinkoffAmortizationMapper.toModel(amortizationRepository.findByUidIn(uids))
                .stream()
                .collect(Collectors.groupingBy(AmortizationModel::getUid));
    }
}
