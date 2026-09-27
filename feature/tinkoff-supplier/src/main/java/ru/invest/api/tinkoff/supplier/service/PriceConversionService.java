package ru.invest.api.tinkoff.supplier.service;

import ru.invest.api.common.model.BondModel;

import java.util.Collection;

public interface PriceConversionService {

    /**
     * Заполняет у облигаций текущую цену в рублях (PriceModel.currentInRub) по курсу ЦБ,
     * при его отсутствии - по budget.org. Курс запрашивается один раз на каждую валюту.
     * Если цены или курса нет, currentInRub остаётся null - облигация считается без цены.
     */
    void fillCurrentInRub(Collection<BondModel> bonds);
}
