package ru.invest.api.common.model.payment;

/**
 * Статус платежа у нас. Статусы провайдера сводятся к этим. SUCCEEDED, FAILED и REFUNDED - конечные: из FAILED и
 * REFUNDED никуда, из SUCCEEDED - только в REFUNDED.
 */
public enum PaymentStatus {
    /** Создан у нас, провайдер ещё не ответил */
    NEW,
    /** Создан у провайдера, ждём оплату или списание */
    PENDING,
    /** Оплачен: тариф выдан или продлён */
    SUCCEEDED,
    /** Отклонён, отменён или истёк: тариф не меняется */
    FAILED,
    /** Деньги возвращены: подписка этого платежа отозвана */
    REFUNDED;

    public boolean isFinal() {
        return this == SUCCEEDED || this == FAILED || this == REFUNDED;
    }
}
