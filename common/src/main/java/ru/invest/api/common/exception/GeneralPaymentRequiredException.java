package ru.invest.api.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.invest.api.common.model.account.PlanFeature;
import ru.invest.api.common.model.account.PlanLimit;

/**
 * Действие не входит в тариф пользователя: лимит исчерпан или фича закрыта (402). Сообщение - для человека, по-русски:
 * UI показывает его как есть.
 */
@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
@Getter
public final class GeneralPaymentRequiredException extends RuntimeException {
    /** id тарифа пользователя */
    private final String plan;
    /** Исчерпанный лимит; null - закрыта фича */
    private final PlanLimit limit;
    /** Значение лимита в тарифе; null - закрыта фича */
    private final Integer max;
    /** Закрытая фича; null - исчерпан лимит */
    private final PlanFeature feature;

    private GeneralPaymentRequiredException(final String message, final String plan, final PlanLimit limit,
                                            final Integer max, final PlanFeature feature) {
        super(message);
        this.plan = plan;
        this.limit = limit;
        this.max = max;
        this.feature = feature;
    }

    public static GeneralPaymentRequiredException limit(final String message, final String plan,
                                                        final PlanLimit limit, final int max) {
        return new GeneralPaymentRequiredException(message, plan, limit, max, null);
    }

    public static GeneralPaymentRequiredException feature(final String message, final String plan,
                                                          final PlanFeature feature) {
        return new GeneralPaymentRequiredException(message, plan, null, null, feature);
    }
}
