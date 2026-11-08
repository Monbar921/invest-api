package ru.invest.api.alert.service.sender;

/**
 * Канал доставки уведомлений. Первая реализация - Telegram; без реализации (бот выключен) уведомления сразу FAILED.
 */
public interface NotificationSender {
    /**
     * Одна попытка отправки. Не бросает исключений: всё - в результате.
     */
    DeliveryResult send(OutgoingNotification notification);
}
