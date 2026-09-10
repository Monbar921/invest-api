package ru.invest.api.tinkoff.supplier.usecase;

import java.util.Set;

public interface GetNeedToUpdateCouponsTickersUseCase {
    Set<String> getTickersToUpdate();
}
