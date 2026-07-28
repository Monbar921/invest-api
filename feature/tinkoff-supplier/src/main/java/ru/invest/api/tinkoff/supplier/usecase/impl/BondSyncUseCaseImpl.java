package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondEntityMapper;
import ru.invest.api.tinkoff.supplier.usecase.BondSyncUseCase;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondApiUseCase;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BondSyncUseCaseImpl implements BondSyncUseCase {
    private final TinkoffBondApiUseCase tinkoffBondApiUseCase;
    private final BondRepository bondRepository;
    private final TinkoffBondEntityMapper tinkoffBondEntityMapper;

    @Override
    @Transactional
    public void syncAll() {
        final Map<String, BondModel> tinkoffBonds = tinkoffBondApiUseCase.getAllBonds();

        if (MapUtils.isEmpty(tinkoffBonds)) {
            return;
        }

        final Map<String, Bond> existingBonds = bondRepository.findAll()
                .stream()
                .collect(Collectors.toMap(Bond::getUid, Function.identity()));

        final List<Bond> toSave = tinkoffBonds.values()
                .stream()
                .map(protoBond -> tinkoffBondEntityMapper.toEntity(protoBond, existingBonds.get(protoBond.getUid())))
                .toList();

        bondRepository.saveAll(toSave);

        final Map<String, Bond> toSaveMap = toSave
                .stream()
                .collect(Collectors.toMap(Bond::getUid, Function.identity()));

        final List<Bond> toDelete = existingBonds
                .values()
                .stream()
                .filter(Objects::nonNull)
                .filter(bond -> toSaveMap.get(bond.getUid()) == null)
                .toList();
    }
}
