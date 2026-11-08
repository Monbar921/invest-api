package ru.invest.api.dto.alert;

public enum NotificationStatusDto {
    /** ждёт отправки или повтора */
    PENDING,
    SENT,
    /** не отправлено и больше не повторяется */
    FAILED
}
