package ru.invest.api.bond.supplier.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.parameters.BondParametersModel;

import java.util.List;

public interface BondUseCase {
    List<BondModel> getForeignCurrencyBonds(BondParametersModel bondParametersModel);

    List<BondModel> getRubbleCurrencyBonds(BondParametersModel bondParametersModel);

    List<BondModel> getAll(BondParametersModel bondParametersModel);

    /**
     * Страница облигаций в иностранной валюте: фильтры и сортировка как в getForeignCurrencyBonds, batchLimit не учитывается.
     */
    Page<BondModel> getForeignCurrencyBondsPage(BondParametersModel bondParametersModel, Pageable pageable);

    /**
     * Страница рублёвых облигаций: фильтры и сортировка как в getRubbleCurrencyBonds, batchLimit не учитывается.
     */
    Page<BondModel> getRubbleCurrencyBondsPage(BondParametersModel bondParametersModel, Pageable pageable);

    /**
     * Страница всех облигаций: фильтры и сортировка как в getAll, batchLimit не учитывается.
     */
    Page<BondModel> getAllPage(BondParametersModel bondParametersModel, Pageable pageable);

    /**
     * Облигация с ценой и купоном вместе с графиком выплат.
     */
    BondModel getByTicker(String ticker);
}
