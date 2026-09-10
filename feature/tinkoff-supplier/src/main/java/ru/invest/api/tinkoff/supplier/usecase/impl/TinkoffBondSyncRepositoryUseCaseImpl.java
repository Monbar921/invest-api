package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondEntityMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondSyncRepositoryUseCase;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TinkoffBondSyncRepositoryUseCaseImpl implements TinkoffBondSyncRepositoryUseCase {
    private final BondRepository bondRepository;
    private final TinkoffBondEntityMapper tinkoffBondEntityMapper;

    @Override
    @Transactional
    public void sync(final Map<String, BondModel> tinkoffBonds) {
        if (MapUtils.isEmpty(tinkoffBonds)) {
            return;
        }

        final List<Bond> existingBonds = bondRepository.findAll();

        final Map<String, Bond> existingBondsMap = existingBonds
                .stream()
                .collect(Collectors.toMap(Bond::getUid, Function.identity()));

        final List<Bond> toSave = tinkoffBonds.values()
                .stream()
                .filter(bondModel -> existingBondsMap.get(bondModel.getUid()) == null)
                .map(bondModel -> tinkoffBondEntityMapper.toEntity(bondModel, existingBondsMap.get(bondModel.getUid())))
                .toList();

        tinkoffBonds.values()
                .stream()
                .filter(bondModel -> existingBondsMap.get(bondModel.getUid()) != null)
                .forEach(bondModel -> tinkoffBondEntityMapper.toEntity(bondModel, existingBondsMap.get(bondModel.getUid())));

        final List<Bond> toDelete = existingBonds
                .stream()
                .filter(Objects::nonNull)
                .filter(bond -> tinkoffBonds.get(bond.getUid()) == null)
                .toList();

        if (CollectionUtils.isNotEmpty(existingBonds) && CollectionUtils.isNotEmpty(toDelete)) {
            existingBonds.removeAll(toDelete);
        }
        if (CollectionUtils.isNotEmpty(toSave)) {
            existingBonds.addAll(toSave);
        }

        bondRepository.saveAll(existingBonds);
    }
}
