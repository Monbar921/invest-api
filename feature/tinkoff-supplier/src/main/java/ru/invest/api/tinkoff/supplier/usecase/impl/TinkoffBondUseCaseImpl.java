package ru.invest.api.tinkoff.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.invest.api.common.entity.Bond;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.repository.BondRepository;
import ru.invest.api.tinkoff.supplier.mapper.TinkoffBondEntityMapper;
import ru.invest.api.tinkoff.supplier.usecase.TinkoffBondUseCase;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TinkoffBondUseCaseImpl implements TinkoffBondUseCase {
    private static final String BOND_NOT_FOUND_MESSAGE = "Bond not found for ticker %s";

    private final TinkoffBondEntityMapper tinkoffBondEntityMapper;
    private final BondRepository bondRepository;

    @Override
    public List<BondModel> getAll() {
        return tinkoffBondEntityMapper.toModel(bondRepository.findAll());
    }

    @Override
    public BondModel findByTicker(final String ticker) {
        final Bond bond = bondRepository.findByTicker(ticker)
                .orElseThrow(() -> new GeneralNotFoundEntityException(
                        ExceptionErrorCode.BOND_NOT_FOUND, BOND_NOT_FOUND_MESSAGE.formatted(ticker)));

        return tinkoffBondEntityMapper.toModel(bond);
    }
}
