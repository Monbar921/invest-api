package ru.invest.api.common.usecase;

import ru.invest.api.common.model.BondModel;

import java.util.Collection;

/**
 * Дополняет облигации данными для скринера, которые не хранятся вместе с облигацией: рейтинг, ликвидность, спред к
 * ОФЗ, база флоатера. Вызывается при загрузке кэша облигаций (весь список) и при чтении карточки.
 * Реализации живут в модулях, где есть их источник данных; каждая заполняет только свои поля и не падает,
 * если источник недоступен, - поле остаётся null.
 */
public interface BondScreenerEnricher {
    /**
     * @param detailed true - карточка облигации: можно добавить подробности (рейтинги всех агентств)
     */
    void enrich(Collection<BondModel> bonds, boolean detailed);
}
