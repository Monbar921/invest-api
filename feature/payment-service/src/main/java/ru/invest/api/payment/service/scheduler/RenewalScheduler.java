package ru.invest.api.payment.service.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.invest.api.payment.service.usecase.RenewalUseCase;

/**
 * Автопродление по расписанию (по умолчанию раз в час); при выключенной оплате ничего не делает. В тестах
 * выключено (renewal.scheduler: false) - тест вызывает RenewalUseCase сам.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ru.invest.api.payments.renewal", name = "scheduler", havingValue = "true",
        matchIfMissing = true)
public class RenewalScheduler {
    private static final String RENEWAL_LOCK = "PaymentRenewal";

    private final RenewalUseCase renewalUseCase;

    @Scheduled(fixedDelayString = "${ru.invest.api.payments.renewal.interval:PT1H}", initialDelayString = "PT1M")
    @SchedulerLock(name = RENEWAL_LOCK, lockAtMostFor = "PT30M")
    public void renew() {
        renewalUseCase.renewDue();
    }
}
