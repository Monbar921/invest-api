package ru.invest.api.bond.supplier.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;

public interface BondUseCase {
    /**
     * Страница облигаций в иностранной валюте: фильтры и сортировка применяются ко всей выборке, затем берётся страница.
     */
    Page<BondModel> getForeignCurrencyBondsPage(BondParametersModel bondParametersModel, Pageable pageable);

    /**
     * Страница рублёвых облигаций: фильтры и сортировка применяются ко всей выборке, затем берётся страница.
     */
    Page<BondModel> getRubbleCurrencyBondsPage(BondParametersModel bondParametersModel, Pageable pageable);

    /**
     * Страница всех облигаций: фильтры и сортировка применяются ко всей выборке, затем берётся страница.
     */
    Page<BondModel> getAllPage(BondParametersModel bondParametersModel, Pageable pageable);

    /**
     * Облигация с ценой и купоном вместе с графиком выплат.
     */
    BondModel getByTicker(String ticker);
}
