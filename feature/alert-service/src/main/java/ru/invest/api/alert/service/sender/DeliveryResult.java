package ru.invest.api.alert.service.sender;

import java.time.Duration;

/**
 * Итог одной попытки отправки.
 *
 * @param retryAfter для RETRY - не раньше чем через (например, ответ 429 Telegram); null - по обычной паузе
 */
public record DeliveryResult(Outcome outcome, String error, Duration retryAfter) {

    public enum Outcome {
        SENT,
        /** временная ошибка: нет связи, лимит - повторить позже */
        RETRY,
        /** повторять бессмысленно: канал не подключён, бот заблокирован, сообщение отклонено */
        FAILED
    }

    public static DeliveryResult sent() {
        return new DeliveryResult(Outcome.SENT, null, null);
    }

    public static DeliveryResult retry(final String error, final Duration retryAfter) {
        return new DeliveryResult(Outcome.RETRY, error, retryAfter);
    }

    public static DeliveryResult failed(final String error) {
        return new DeliveryResult(Outcome.FAILED, error, null);
    }
}
