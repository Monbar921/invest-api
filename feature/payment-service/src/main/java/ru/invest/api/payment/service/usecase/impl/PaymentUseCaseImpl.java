package ru.invest.api.payment.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.account.service.config.PlanProperties;
import ru.invest.api.account.service.model.PeriodTexts;
import ru.invest.api.account.service.model.PlanModel;
import ru.invest.api.account.service.usecase.AccountPlanUseCase;
import ru.invest.api.common.entity.AccountAutoRenewal;
import ru.invest.api.common.entity.Payment;
import ru.invest.api.common.exception.GeneralNotFoundEntityException;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.payment.PaymentKind;
import ru.invest.api.common.model.payment.PaymentStatus;
import ru.invest.api.common.repository.AccountAutoRenewalRepository;
import ru.invest.api.common.repository.PaymentRepository;
import ru.invest.api.payment.service.config.PaymentProperties;
import ru.invest.api.payment.service.model.PaymentModel;
import ru.invest.api.payment.service.model.PaymentsInfoModel;
import ru.invest.api.payment.service.provider.PaymentProvider;
import ru.invest.api.payment.service.provider.PaymentProviderException;
import ru.invest.api.payment.service.provider.model.CreatePaymentCommand;
import ru.invest.api.payment.service.provider.model.ProviderPayment;
import ru.invest.api.payment.service.usecase.PaymentUseCase;

import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentUseCaseImpl implements PaymentUseCase {
    private static final String PAYMENT_ID = "{paymentId}";

    private final PaymentProperties properties;
    private final PlanProperties planProperties;
    private final ActiveProvider activeProvider;
    private final PaymentApplier applier;
    private final PaymentRepository paymentRepository;
    private final AccountAutoRenewalRepository renewalRepository;
    private final AccountPlanUseCase accountPlanUseCase;
    private final DateTimeMapper dateTimeMapper;

    @Override
    @Transactional(readOnly = true)
    public PaymentsInfoModel info(final String userId) {
        final PaymentsInfoModel.AutoRenewal autoRenewal = renewalRepository.findByUserIdAndCancelledAtIsNull(userId)
                .map(renewal -> {
                    final PlanModel plan = accountPlanUseCase.findPlan(renewal.getPlan()).orElse(null);
                    return new PaymentsInfoModel.AutoRenewal(renewal.getPlan(),
                            plan == null ? renewal.getPlan() : plan.title(), renewal.getCardPan(),
                            renewal.getNextChargeAt().atZone(ZoneOffset.UTC)
                                    .withZoneSameInstant(planProperties.getZone()).toLocalDate(),
                            plan == null ? null : plan.price());
                })
                .orElse(null);
        return new PaymentsInfoModel(properties.isEnabled(), properties.getOfferUrl(), properties.getPrivacyUrl(),
                autoRenewal);
    }

    @Override
    public PaymentModel purchase(final String userId, final String planId) {
        if (!properties.isEnabled()) {
            throw unprocessable("Оплата пока недоступна");
        }
        final PlanModel plan = accountPlanUseCase.findPlan(planId)
                .filter(PlanModel::purchasable)
                .orElseThrow(() -> unprocessable("Тариф %s не продаётся".formatted(planId)));
        final PaymentProvider provider = activeProvider.get();
        final String orderId = UUID.randomUUID().toString();
        // платёж сохраняется до запроса к провайдеру: webhook найдёт его по номеру заказа
        final Payment payment = paymentRepository.save(new Payment()
                .setUserId(userId)
                .setPlan(plan.id())
                .setPeriod(plan.period().toString())
                .setAmount(plan.price())
                .setKind(PaymentKind.PURCHASE)
                .setStatus(PaymentStatus.NEW)
                .setProvider(provider.id())
                .setOrderId(orderId)
                .setCreatedAt(dateTimeMapper.getNow()));
        final ProviderPayment created;
        try {
            created = provider.create(new CreatePaymentCommand(orderId, plan.price(), description(plan), userId,
                    url(properties.getSuccessUrl(), payment.getId()), url(properties.getFailUrl(), payment.getId()),
                    properties.getNotificationUrl()));
        } catch (final PaymentProviderException e) {
            log.error("Payment {} of user {} was not created: {}", payment.getId(), userId, e.getMessage());
            applier.markFailed(payment.getId(), e.getMessage());
            throw e;
        }
        applier.apply(payment.getId(), created);
        log.info("Payment {} for plan {} created for user {}", payment.getId(), plan.id(), userId);
        return toModel(paymentRepository.findById(payment.getId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentModel> history(final String userId) {
        return paymentRepository.findByUserIdAndStatusNotOrderByCreatedAtDescIdDesc(userId, PaymentStatus.NEW)
                .stream()
                .map(this::toModel)
                .toList();
    }

    @Override
    public PaymentModel get(final String userId, final long paymentId) {
        final Payment payment = paymentRepository.findByIdAndUserId(paymentId, userId)
                .orElseThrow(() -> new GeneralNotFoundEntityException(ExceptionErrorCode.PAYMENT_NOT_FOUND,
                        "Payment %d not found".formatted(paymentId)));
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getProviderPaymentId() != null) {
            refresh(payment);
            return toModel(paymentRepository.findById(paymentId).orElseThrow());
        }
        return toModel(payment);
    }

    @Override
    @Transactional
    public void cancelAutoRenewal(final String userId) {
        renewalRepository.findByUserIdAndCancelledAtIsNull(userId).ifPresent(renewal -> {
            renewalRepository.save(renewal.setCancelledAt(dateTimeMapper.getNow())
                    .setCancelReason(AccountAutoRenewal.CANCEL_USER));
            log.info("Auto renewal {} cancelled by user {}", renewal.getId(), userId);
        });
    }

    @Override
    public String handleNotification(final String providerId, final String body) {
        final PaymentProvider provider = activeProvider.get();
        if (!provider.id().equals(providerId)) {
            throw new GeneralNotFoundEntityException(ExceptionErrorCode.NOT_FOUND,
                    "Payment provider %s is not configured".formatted(providerId));
        }
        final ProviderPayment state = provider.parseNotification(body);
        paymentRepository.findByProviderAndOrderId(provider.id(), state.orderId()).ifPresentOrElse(
                payment -> applier.apply(payment.getId(), state),
                // не наш заказ (другая система на том же терминале): подтверждаем, иначе провайдер будет повторять
                () -> log.warn("Payment notification for unknown order {} ignored", state.orderId()));
        return provider.notificationResponse();
    }

    /** Статус у провайдера; провайдер не ответил - остаётся как есть */
    private void refresh(final Payment payment) {
        if (!activeProvider.get().id().equals(payment.getProvider())) {
            return;
        }
        try {
            applier.apply(payment.getId(), activeProvider.get().getState(payment.getProviderPaymentId()));
        } catch (final PaymentProviderException e) {
            log.warn("Payment {} state is not refreshed: {}", payment.getId(), e.getMessage());
        }
    }

    static String description(final PlanModel plan) {
        return "Тариф «%s» на %s".formatted(plan.title(), PeriodTexts.format(plan.period()));
    }

    private static String url(final String template, final long paymentId) {
        return template.replace(PAYMENT_ID, String.valueOf(paymentId));
    }

    private PaymentModel toModel(final Payment payment) {
        final String title = accountPlanUseCase.findPlan(payment.getPlan()).map(PlanModel::title)
                .orElse(payment.getPlan());
        final boolean awaiting = payment.getStatus() == PaymentStatus.PENDING;
        return new PaymentModel(payment.getId(), payment.getPlan(), title, Period.parse(payment.getPeriod()),
                payment.getAmount(), payment.getKind(), payment.getStatus(), payment.getCardPan(),
                payment.getErrorMessage(), awaiting ? payment.getConfirmationUrl() : null, payment.getCreatedAt(),
                payment.getPaidAt());
    }

    private static GeneralUnprocessableEntityException unprocessable(final String message) {
        return new GeneralUnprocessableEntityException(ExceptionErrorCode.PAYMENT_INVALID, message);
    }
}
