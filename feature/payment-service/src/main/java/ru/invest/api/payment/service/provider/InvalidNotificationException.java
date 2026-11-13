package ru.invest.api.payment.service.provider;

/**
 * Webhook не от провайдера: тело не разбирается или подпись не сходится. Ответ - 400, ничего не меняется.
 */
public class InvalidNotificationException extends RuntimeException {
    public InvalidNotificationException(final String message) {
        super(message);
    }
}
