package ru.invest.api.payment.service.provider.tbank;

import ru.invest.api.common.model.payment.PaymentStatus;

import java.util.Set;

/**
 * Статусы Т-Кассы → наши. Одностадийная оплата: успех - CONFIRMED.
 */
public final class TbankStatuses {
    private static final Set<String> SUCCEEDED = Set.of("CONFIRMED");
    private static final Set<String> FAILED = Set.of("REJECTED", "AUTH_FAIL", "CANCELED", "DEADLINE_EXPIRED",
            "REVERSED", "PARTIAL_REVERSED");
    /** Частичный возврат (PARTIAL_REFUNDED) тариф не отзывает: платёж остаётся оплаченным */
    private static final Set<String> REFUNDED = Set.of("REFUNDED");

    private TbankStatuses() {
    }

    /** Статуса нет в ответе - PENDING: итог узнаем позже */
    public static PaymentStatus toStatus(final String status) {
        if (status == null) {
            return PaymentStatus.PENDING;
        }
        if (SUCCEEDED.contains(status)) {
            return PaymentStatus.SUCCEEDED;
        }
        if (FAILED.contains(status)) {
            return PaymentStatus.FAILED;
        }
        if (REFUNDED.contains(status)) {
            return PaymentStatus.REFUNDED;
        }
        // NEW, FORM_SHOWED, AUTHORIZING, 3DS_CHECKING, AUTHORIZED, CONFIRMING, PARTIAL_REFUNDED и неизвестные
        return PaymentStatus.PENDING;
    }

    public static boolean isFailed(final String status) {
        return status != null && FAILED.contains(status);
    }
}
