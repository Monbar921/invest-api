package ru.invest.api.alert.service.usecase;

import ru.invest.api.alert.service.model.SavedFilterChangeModel;
import ru.invest.api.alert.service.model.SavedFilterModel;

import java.util.List;

/**
 * Сохранённые фильтры пользователя и переключатель уведомлений по ним. Чужой или несуществующий фильтр -
 * GeneralNotFoundEntityException (404).
 */
public interface SavedFilterUseCase {
    /** Фильтры пользователя по названию */
    List<SavedFilterModel> getFilters(String userId);

    SavedFilterModel getFilter(String userId, Long id);

    /**
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException фильтр с таким названием уже есть
     * @throws ru.invest.api.common.exception.GeneralPaymentRequiredException     фильтров (или правил уведомлений -
     *                                                                            при notify) столько, сколько
     *                                                                            разрешает тариф
     */
    SavedFilterModel create(String userId, SavedFilterChangeModel filter);

    /**
     * Новые параметры или включение уведомлений - текущая выборка становится отправной точкой: уведомления придут
     * только об облигациях, которые войдут в неё потом. Выключение уведомлений удаляет правило.
     */
    SavedFilterModel update(String userId, Long id, SavedFilterChangeModel change);

    /** Удаляет фильтр вместе с правилом уведомлений; история уведомлений остаётся */
    void delete(String userId, Long id);
}
