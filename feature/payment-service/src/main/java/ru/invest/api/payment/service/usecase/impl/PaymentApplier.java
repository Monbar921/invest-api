package ru.invest.api.payment.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.account.service.model.PaidSubscriptionModel;
import ru.invest.api.account.service.usecase.AccountPlanUseCase;
import ru.invest.api.common.entity.AccountAutoRenewal;
import ru.invest.api.common.entity.Payment;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.payment.PaymentKind;
import ru.invest.api.common.model.payment.PaymentStatus;
import ru.invest.api.common.repository.AccountAutoRenewalRepository;
import ru.invest.api.common.repository.AccountSubscriptionRepository;
import ru.invest.api.common.repository.PaymentRepository;
import ru.invest.api.payment.service.config.PaymentProperties;
import ru.invest.api.payment.service.provider.model.ProviderPayment;

import java.time.LocalDateTime;
import java.time.Period;
import java.util.Objects;

/**
 * Применяет состояние платежа от провайдера: ответ на создание и списание, запрос статуса, webhook. Всё под
 * блокировкой строки платежа, поэтому повторный или одновременный webhook не выдаёт тариф дважды: тариф выдаётся
 * только при переходе в SUCCEEDED, а из конечного статуса назад не переходят.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PaymentApplier {
    private final PaymentRepository paymentRepository;
    private final AccountAutoRenewalRepository renewalRepository;
    private final AccountSubscriptionRepository subscriptionRepository;
    private final AccountPlanUseCase accountPlanUseCase;
    private final PaymentProperties properties;
    private final DateTimeMapper dateTimeMapper;

    @Transactional
    public void apply(final long paymentId, final ProviderPayment state) {
        final Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
        if (state.providerPaymentId() != null && payment.getProviderPaymentId() != null
                && !state.providerPaymentId().equals(payment.getProviderPaymentId())) {
            log.warn("Payment {}: provider payment id {} does not match {}, ignored", paymentId,
                    state.providerPaymentId(), payment.getProviderPaymentId());
            return;
        }
        if (state.amount() != null && state.amount().compareTo(payment.getAmount()) != 0) {
            log.error("Payment {}: provider amount {} does not match {}, ignored", paymentId, state.amount(),
                    payment.getAmount());
            return;
        }
        final LocalDateTime now = dateTimeMapper.getNow();
        if (payment.getProviderPaymentId() == null) {
            payment.setProviderPaymentId(state.providerPaymentId());
        }
        if (state.cardPan() != null) {
            payment.setCardPan(state.cardPan());
        }
        final PaymentStatus current = payment.getStatus();
        final PaymentStatus next = state.status();
        if (current == PaymentStatus.SUCCEEDED && next == PaymentStatus.REFUNDED) {
            refund(payment, now);
        } else if (current == PaymentStatus.SUCCEEDED && next == PaymentStatus.SUCCEEDED) {
            // повтор успеха: тариф уже выдан; карта могла прийти только в webhook после опроса статуса
            enableAutoRenewal(payment, state, null, now);
        } else if (current.isFinal()) {
            log.info("Payment {} is already {}, provider status {} ignored", paymentId, current,
                    state.providerStatus());
            return;
        } else if (next == PaymentStatus.SUCCEEDED) {
            succeed(payment, state, now);
        } else if (next == PaymentStatus.FAILED || next == PaymentStatus.REFUNDED) {
            fail(payment, state, now);
        } else {
            payment.setStatus(PaymentStatus.PENDING);
            if (state.confirmationUrl() != null) {
                payment.setConfirmationUrl(state.confirmationUrl());
            }
        }
        if (state.providerStatus() != null) {
            payment.setProviderStatus(state.providerStatus());
        }
        paymentRepository.save(payment.setUpdatedAt(now));
    }

    /** Платёж не создан у провайдера или списание не прошло без ответа провайдера */
    @Transactional
    public void markFailed(final long paymentId, final String message) {
        apply(paymentId, new ProviderPayment(null, null, PaymentStatus.FAILED, null, null, null, null, null, message));
    }

    private void succeed(final Payment payment, final ProviderPayment state, final LocalDateTime now) {
        final PaidSubscriptionModel subscription = accountPlanUseCase.grantPaid(payment.getUserId(), payment.getPlan(),
                Period.parse(payment.getPeriod()), payment.getId());
        payment.setStatus(PaymentStatus.SUCCEEDED)
                .setPaidAt(now)
                .setSubscriptionId(subscription.subscriptionId())
                .setConfirmationUrl(null)
                .setErrorMessage(null);
        log.info("Payment {} of user {} succeeded: plan {} until {} inclusive", payment.getId(), payment.getUserId(),
                payment.getPlan(), subscription.lastDay());
        final LocalDateTime nextCharge = subscription.validUntil().minus(properties.getRenewal().getChargeBefore());
        if (payment.getKind() == PaymentKind.RENEWAL) {
            renewalRepository.findById(payment.getAutoRenewalId())
                    .filter(renewal -> renewal.getCancelledAt() == null)
                    .ifPresent(renewal -> renewalRepository.save(renewal.setNextChargeAt(nextCharge)
                            .setFailedAttempts(0)));
            return;
        }
        enableAutoRenewal(payment, state, nextCharge, now);
    }

    /**
     * Покупка с сохранённой картой включает автопродление (прежнее отключается). Без карты прежнее автопродление того
     * же тарифа сдвигается на новый срок, другого тарифа - отключается.
     *
     * @param nextCharge когда списать; null - посчитать от подписки платежа
     */
    private void enableAutoRenewal(final Payment payment, final ProviderPayment state, final LocalDateTime nextCharge,
                                   final LocalDateTime now) {
        if (payment.getKind() != PaymentKind.PURCHASE) {
            return;
        }
        if (state.rebillId() == null) {
            if (nextCharge != null) {
                renewalRepository.findByUserIdAndCancelledAtIsNull(payment.getUserId()).ifPresent(renewal -> {
                    if (renewal.getPlan().equals(payment.getPlan())) {
                        renewalRepository.save(renewal.setNextChargeAt(nextCharge).setFailedAttempts(0));
                    } else {
                        cancel(renewal, AccountAutoRenewal.CANCEL_REPLACED, now);
                    }
                });
            }
            return;
        }
        if (renewalRepository.existsByPaymentId(payment.getId())) {
            return;
        }
        final LocalDateTime chargeAt = nextCharge != null ? nextCharge : subscriptionRepository
                .findById(payment.getSubscriptionId())
                .map(subscription -> subscription.getValidUntil().minus(properties.getRenewal().getChargeBefore()))
                .orElse(null);
        if (chargeAt == null) {
            return;
        }
        renewalRepository.findByUserIdAndCancelledAtIsNull(payment.getUserId())
                .ifPresent(renewal -> cancel(renewal, AccountAutoRenewal.CANCEL_REPLACED, now));
        // отключённое должно попасть в БД раньше нового: у пользователя одно действующее (уникальный индекс)
        renewalRepository.flush();
        final AccountAutoRenewal renewal = renewalRepository.save(new AccountAutoRenewal()
                .setUserId(payment.getUserId())
                .setPlan(payment.getPlan())
                .setProvider(payment.getProvider())
                .setRebillId(state.rebillId())
                .setCardPan(Objects.requireNonNullElse(state.cardPan(), payment.getCardPan()))
                .setNextChargeAt(chargeAt)
                .setPaymentId(payment.getId())
                .setCreatedAt(now));
        log.info("Auto renewal {} of plan {} enabled for user {}, next charge at {} UTC", renewal.getId(),
                payment.getPlan(), payment.getUserId(), chargeAt);
    }

    private void fail(final Payment payment, final ProviderPayment state, final LocalDateTime now) {
        payment.setStatus(PaymentStatus.FAILED)
                .setErrorMessage(state.errorMessage())
                .setConfirmationUrl(null);
        log.info("Payment {} of user {} failed: {} {}", payment.getId(), payment.getUserId(), state.providerStatus(),
                state.errorMessage());
        if (payment.getKind() != PaymentKind.RENEWAL) {
            return;
        }
        renewalRepository.findById(payment.getAutoRenewalId())
                .filter(renewal -> renewal.getCancelledAt() == null)
                .ifPresent(renewal -> {
                    final int attempts = renewal.getFailedAttempts() + 1;
                    renewal.setFailedAttempts(attempts);
                    if (attempts >= properties.getRenewal().getMaxAttempts()) {
                        cancel(renewal, AccountAutoRenewal.CANCEL_PAYMENT_FAILED, now);
                    } else {
                        renewalRepository.save(renewal.setNextChargeAt(
                                now.plus(properties.getRenewal().getRetryInterval())));
                    }
                });
    }

    /** Возврат: подписка платежа отзывается, если ещё действует; автопродление пользователя отключается */
    private void refund(final Payment payment, final LocalDateTime now) {
        payment.setStatus(PaymentStatus.REFUNDED);
        if (payment.getSubscriptionId() != null) {
            accountPlanUseCase.revokePaid(payment.getSubscriptionId());
        }
        renewalRepository.findByUserIdAndCancelledAtIsNull(payment.getUserId())
                .ifPresent(renewal -> cancel(renewal, AccountAutoRenewal.CANCEL_REFUNDED, now));
        log.info("Payment {} of user {} refunded", payment.getId(), payment.getUserId());
    }

    private void cancel(final AccountAutoRenewal renewal, final String reason, final LocalDateTime now) {
        renewalRepository.save(renewal.setCancelledAt(now).setCancelReason(reason));
        log.info("Auto renewal {} of user {} cancelled: {}", renewal.getId(), renewal.getUserId(), reason);
    }
}
