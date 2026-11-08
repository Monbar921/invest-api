package ru.invest.api.alert.service.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.invest.api.alert.service.model.AlertChangeModel;
import ru.invest.api.alert.service.model.AlertModel;
import ru.invest.api.alert.service.model.AlertRuleModel;
import ru.invest.api.alert.service.model.NotificationModel;

import java.util.List;

/**
 * Правила уведомлений пользователя. Чужое или несуществующее правило - GeneralNotFoundEntityException (404).
 */
public interface AlertUseCase {
    /**
     * @param ticker null - все правила пользователя
     */
    List<AlertModel> getAlerts(String userId, String ticker);

    /**
     * Новое включённое правило; если условие уже выполнено - уведомление сразу в очередь.
     *
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException параметры не подходят типу, такое
     *                                                                            правило по дате уже есть, правил
     *                                                                            больше 50 или у облигации нет цены
     *                                                                            (для изменения от текущей цены)
     */
    AlertModel create(String userId, AlertRuleModel rule);

    /**
     * Изменение порога или включение заново взводит порог (а изменение от цены отсчитывается от текущей цены) и сразу
     * проверяет правило.
     */
    AlertModel update(String userId, Long id, AlertChangeModel change);

    void delete(String userId, Long id);

    Page<NotificationModel> getNotifications(String userId, Pageable pageable);
}
