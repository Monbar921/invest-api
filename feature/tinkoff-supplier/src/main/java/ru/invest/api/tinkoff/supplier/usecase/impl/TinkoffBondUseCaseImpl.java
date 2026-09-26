package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.AuditModel;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondEntityMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TinkoffBondUseCaseImpl implements TinkoffBondUseCase {
    private static final String BOND_NOT_FOUND_MESSAGE = "Bond not found for ticker %s";

    private final TinkoffBondEntityMapper tinkoffBondEntityMapper;
    private final BondRepository bondRepository;

    @Override
    @Transactional(readOnly = true)
    public List<BondModel> getAll() {
        return tinkoffBondEntityMapper.toModel(bondRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public BondModel findByTicker(final String ticker) {
        final Bond bond = bondRepository.findByTicker(ticker)
                .orElseThrow(() -> new GeneralNotFoundEntityException(
                        ExceptionErrorCode.BOND_NOT_FOUND, BOND_NOT_FOUND_MESSAGE.formatted(ticker)));

        return tinkoffBondEntityMapper.toModel(bond);
    }

    @Override
    @Transactional
    public void save(final Map<String, BondModel> tinkoffBonds, final AuditModel audit) {
        if (MapUtils.isEmpty(tinkoffBonds)) {
            return;
        }

        tinkoffBonds.values().forEach(bondModel -> bondModel.setLogged(audit));

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
