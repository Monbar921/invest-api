package ru.invest.api.alert.service.usecase;

/**
 * Разбор очереди уведомлений: отправка через NotificationSender, повторы с растущей паузой, отказ через сутки.
 */
public interface NotificationDispatchUseCase {
    /**
     * Отправить уведомления, у которых подошло время попытки (не больше batch-size за раз).
     *
     * @return сколько отправлено
     */
    int dispatch();

    /**
     * Разобрать очередь в фоне сейчас, не дожидаясь расписания: после срабатывания правила. Ничего не делает, если
     * разбор выключен (ru.invest.api.alerts.dispatcher.enabled: false).
     */
    void dispatchSoon();

    /**
     * Удалить отправленные и неотправленные уведомления старше срока хранения.
     */
    int cleanUp();
}
