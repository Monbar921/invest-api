package ru.invest.api.tinkoff.supplier.service;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.calculation.ProfitCalculationModel;
import ru.invest.api.common.model.calculation.ProfitCalculationRequestModel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface ProfitCalculationService {

    /**
     * Доход за период владения облигацией: что заплатим при покупке (цена + НКД), что получим за период (купоны,
     * частичные погашения номинала) и при выходе (продажа + НКД, выкуп по оферте или погашение), НДФЛ по ставке из запроса.
     *
     * @param bond облигация с ценой, купоном с графиком выплат и будущими погашениями номинала (amortizations)
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException если параметры не подходят облигации
     */
    ProfitCalculationModel calculate(BondModel bond, ProfitCalculationRequestModel request);

    /**
     * НКД одной облигации на дату - по тому же правилу, что в расчёте дохода: на сегодня - значение из T-Invest API,
     * на другие даты - доля ближайшего купона пропорционально дням с начала купонного периода.
     *
     * @param bond облигация с ценой (номинал, НКД) и графиком купонов
     * @return НКД в валюте номинала; пусто - размер ближайшего купона не объявлен или у облигации нет номинала
     */
    Optional<BigDecimal> accruedInterestAt(BondModel bond, LocalDate date);
}
