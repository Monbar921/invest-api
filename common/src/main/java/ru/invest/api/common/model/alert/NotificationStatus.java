package ru.invest.api.common.model.alert;

/**
 * Состояние уведомления в очереди.
 */
public enum NotificationStatus {
    /** ждёт отправки или повтора */
    PENDING,
    SENT,
    /** не отправлено и больше не повторяется: канал не подключён, бот заблокирован, сутки без связи */
    FAILED
}
