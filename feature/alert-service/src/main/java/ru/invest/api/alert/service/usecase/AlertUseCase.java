package ru.invest.api.alert.service.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.invest.api.alert.service.model.AlertModel;
import ru.invest.api.alert.service.model.NotificationModel;
import ru.invest.api.common.model.alert.AlertType;

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
     * Новое включённое правило; если событие уже в окне - уведомление сразу в очередь.
     *
     * @throws ru.invest.api.common.exception.GeneralUnprocessableEntityException days вне 1..60, такое правило уже
     *                                                                            есть или правил больше 50
     */
    AlertModel create(String userId, String ticker, AlertType type, Integer days);

    /**
     * @param days    null - не менять
     * @param enabled null - не менять
     */
    AlertModel update(String userId, Long id, Integer days, Boolean enabled);

    void delete(String userId, Long id);

    Page<NotificationModel> getNotifications(String userId, Pageable pageable);
}
