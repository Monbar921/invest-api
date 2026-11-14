package ru.invest.api.alert.service.model;

import ru.invest.api.bond.supplier.model.BondScope;
import ru.invest.api.common.model.parameters.BondParametersRequestModel;

import java.time.LocalDateTime;

/**
 * Сохранённый фильтр.
 *
 * @param parameters фильтры и сортировка, как пришли от пользователя
 * @param notifications уведомления включены: правило SAVED_FILTER есть и включено
 * @param alertId    правило уведомлений; null - нет
 * @param matches    сколько облигаций в выборке сейчас; null - кэш облигаций ещё не загружен
 * @param createdAt  UTC
 * @param updatedAt  UTC
 */
public record SavedFilterModel(Long id,
                               String name,
                               BondScope scope,
                               BondParametersRequestModel parameters,
                               boolean notifications,
                               Long alertId,
                               Integer matches,
                               LocalDateTime createdAt,
                               LocalDateTime updatedAt) {
}
