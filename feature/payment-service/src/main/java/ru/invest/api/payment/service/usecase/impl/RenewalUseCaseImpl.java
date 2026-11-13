package ru.invest.api.payment.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.invest.api.account.service.model.CurrentSubscriptionModel;
import ru.invest.api.account.service.model.PlanModel;
import ru.invest.api.account.service.usecase.AccountPlanUseCase;
import ru.invest.api.common.entity.AccountAutoRenewal;
import ru.invest.api.common.entity.Payment;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.payment.PaymentKind;
import ru.invest.api.common.model.payment.PaymentStatus;
import ru.invest.api.common.repository.AccountAutoRenewalRepository;
import ru.invest.api.common.repository.PaymentRepository;
import ru.invest.api.payment.service.config.PaymentProperties;
import ru.invest.api.payment.service.provider.PaymentProvider;
import ru.invest.api.payment.service.provider.PaymentProviderException;
import ru.invest.api.payment.service.provider.model.ChargeCommand;
import ru.invest.api.payment.service.provider.model.ProviderPayment;
import ru.invest.api.payment.service.usecase.RenewalUseCase;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Автопродление. Списание создаётся, когда до конца подписки осталось charge-before; не прошло - повтор через
 * retry-interval, после max-attempts неудач подряд автопродление отключается и тариф заканчивается в срок. Пока у
 * прошлого списания нет итога, новое не создаётся - его статус запрашивается у провайдера.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RenewalUseCaseImpl implements RenewalUseCase {
    private static final List<PaymentStatus> UNFINISHED = List.of(PaymentStatus.NEW, PaymentStatus.PENDING);

    private final PaymentProperties properties;
    private final ActiveProvider activeProvider;
    private final PaymentApplier applier;
    private final PaymentRepository paymentRepository;
    private final AccountAutoRenewalRepository renewalRepository;
    private final AccountPlanUseCase accountPlanUseCase;
    private final DateTimeMapper dateTimeMapper;

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public int renewDue() {
        if (!properties.isEnabled() || !properties.getRenewal().isEnabled()) {
            return 0;
        }
        final List<Long> due = renewalRepository.findDueIds(dateTimeMapper.getNow());
        for (final Long id : due) {
            try {
                renew(id);
            } catch (final RuntimeException e) {
                log.error("Auto renewal {} failed", id, e);
            }
        }
        if (!due.isEmpty()) {
            log.info("Auto renewals processed: {}", due.size());
        }
        return due.size();
    }

    private void renew(final long renewalId) {
        final LocalDateTime now = dateTimeMapper.getNow();
        final AccountAutoRenewal renewal = renewalRepository.findById(renewalId).orElseThrow();
        if (renewal.getCancelledAt() != null || renewal.getNextChargeAt().isAfter(now)) {
            return;
        }
        final PaymentProvider provider = activeProvider.get();
        final Optional<Payment> unfinished = paymentRepository.findFirstByAutoRenewalIdAndStatusInOrderByIdDesc(
                renewalId, UNFINISHED);
        if (unfinished.isPresent()) {
            checkUnfinished(unfinished.get(), provider);
            return;
        }
        // шедулер долго не работал: списывать задним числом за давно закончившийся срок не будем
        final LocalDateTime lastChance = renewal.getNextChargeAt().plus(properties.getRenewal().getRetryInterval()
                .multipliedBy(properties.getRenewal().getMaxAttempts()));
        if (lastChance.isBefore(now)) {
            cancel(renewal, AccountAutoRenewal.CANCEL_EXPIRED, now);
            return;
        }
        final Optional<PlanModel> plan = accountPlanUseCase.findPlan(renewal.getPlan()).filter(PlanModel::purchasable);
        if (plan.isEmpty()) {
            cancel(renewal, AccountAutoRenewal.CANCEL_PLAN_NOT_SOLD, now);
            return;
        }
        final CurrentSubscriptionModel current = accountPlanUseCase.current(renewal.getUserId());
        if (!current.defaultPlan() && !current.plan().equals(renewal.getPlan())) {
            cancel(renewal, AccountAutoRenewal.CANCEL_REPLACED, now);
            return;
        }
        // срок списания сдвигается при каждой оплате (PaymentApplier), поэтому здесь он уже верный
        charge(renewal, plan.get(), provider, now);
    }

    private void charge(final AccountAutoRenewal renewal, final PlanModel plan, final PaymentProvider provider,
                        final LocalDateTime now) {
        final String orderId = UUID.randomUUID().toString();
        final Payment payment = paymentRepository.save(new Payment()
                .setUserId(renewal.getUserId())
                .setPlan(plan.id())
                .setPeriod(plan.period().toString())
                .setAmount(plan.price())
                .setKind(PaymentKind.RENEWAL)
                .setStatus(PaymentStatus.NEW)
                .setProvider(provider.id())
                .setOrderId(orderId)
                .setAutoRenewalId(renewal.getId())
                .setCardPan(renewal.getCardPan())
                .setCreatedAt(now));
        final ProviderPayment result;
        try {
            result = provider.charge(new ChargeCommand(orderId, plan.price(), PaymentUseCaseImpl.description(plan),
                    renewal.getRebillId(), properties.getNotificationUrl()));
        } catch (final PaymentProviderException e) {
            log.warn("Auto renewal {} payment {} was not created: {}", renewal.getId(), payment.getId(),
                    e.getMessage());
            applier.markFailed(payment.getId(), e.getMessage());
            return;
        }
        applier.apply(payment.getId(), result);
        log.info("Auto renewal {} of user {}: payment {} is {}", renewal.getId(), renewal.getUserId(),
                payment.getId(), result.status());
    }

    private void checkUnfinished(final Payment payment, final PaymentProvider provider) {
        if (payment.getProviderPaymentId() == null) {
            // платёж не дошёл до провайдера (процесс упал между сохранением и запросом): денег не списано
            applier.markFailed(payment.getId(), "Платёж не создан у провайдера");
            return;
        }
        try {
            applier.apply(payment.getId(), provider.getState(payment.getProviderPaymentId()));
        } catch (final PaymentProviderException e) {
            log.warn("Auto renewal payment {} state is unknown: {}", payment.getId(), e.getMessage());
        }
    }

    private void cancel(final AccountAutoRenewal renewal, final String reason, final LocalDateTime now) {
        renewalRepository.save(renewal.setCancelledAt(now).setCancelReason(reason));
        log.info("Auto renewal {} of user {} cancelled: {}", renewal.getId(), renewal.getUserId(), reason);
    }
}
