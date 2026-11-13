package ru.invest.api.payment.service.provider;

/**
 * Провайдер отказал или не ответил. Сообщение - для лога, пользователю - общее «платёжный сервис не ответил» (502).
 */
public class PaymentProviderException extends RuntimeException {
    public PaymentProviderException(final String message) {
        super(message);
    }

    public PaymentProviderException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
