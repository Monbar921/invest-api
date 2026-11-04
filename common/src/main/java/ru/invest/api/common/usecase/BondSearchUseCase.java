package ru.invest.api.common.usecase;

import ru.invest.api.common.model.BondModel;

import java.util.Collection;
import java.util.List;

public interface BondSearchUseCase {
    /**
     * Нестрогий поиск по тикеру, ISIN и названию без учёта регистра и «ё». Слова запроса ищутся в любом порядке.
     * Сначала точные совпадения тикера или ISIN, потом совпадения с начала тикера, ISIN, названия или слова в названии,
     * потом остальные. Запрос короче двух символов ничего не находит.
     */
    List<BondModel> search(Collection<BondModel> bonds, String query, int limit);
}
