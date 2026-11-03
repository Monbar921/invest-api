package ru.invest.api.tinkoff.supplier.usecase;

import ru.invest.api.common.model.AmortizationModel;
import ru.invest.api.common.model.ShortProductModel;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TinkoffAmortizationUseCase {
    /**
     * Облигации с амортизацией.
     */
    List<ShortProductModel> getAmortizingBonds();

    /**
     * Заменяет графики амортизации облигаций.
     *
     * @param amortizations графики по uid облигации
     */
    void save(Map<String, List<AmortizationModel>> amortizations);

    /**
     * Графики амортизации по uid облигации.
     */
    Map<String, List<AmortizationModel>> findByUids(Set<String> uids);
}
