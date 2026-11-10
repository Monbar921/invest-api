package ru.invest.api.bond.supplier.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.invest.api.bond.supplier.collection.BondCollection;
import ru.invest.api.common.model.BondModel;

import java.util.List;

/**
 * Подборки облигаций на готовых фильтрах.
 */
public interface BondCollectionUseCase {
    List<BondCollection> getCollections();

    /**
     * @throws ru.invest.api.common.exception.GeneralNotFoundEntityException подборки нет
     */
    BondCollection getCollection(String slug);

    /**
     * Страница облигаций подборки: те же фильтры и сортировка, что у списка облигаций.
     */
    Page<BondModel> getPage(BondCollection collection, Pageable pageable);
}
