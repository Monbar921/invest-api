package ru.invest.api.news.supplier.usecase;

import ru.invest.api.common.model.news.IssuerPageModel;

import java.util.List;
import java.util.Optional;

/**
 * Эмитенты облигаций по данным Мосбиржи (таблица bond_issuer).
 */
public interface IssuerUseCase {
    /**
     * Эмитент с выпусками из системы.
     *
     * @param id id эмитента на Мосбирже
     * @throws ru.invest.api.common.exception.GeneralNotFoundEntityException эмитента нет
     */
    IssuerPageModel getIssuer(Long id);

    /**
     * Эмитент выпуска без списка выпусков; пусто - эмитент неизвестен.
     */
    Optional<IssuerPageModel> findByIsin(String isin);

    /**
     * id эмитентов, у которых есть хотя бы один выпуск в системе.
     */
    List<Long> getIssuerIds();
}
