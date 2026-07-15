package ru.invest.api.tinkoff.supplier.usecase;

import ru.tinkoff.piapi.contract.v1.Bond;

import java.util.Map;

public interface TinkoffBondApiUseCase {
    Map<String, Bond> getAllBonds();
}
