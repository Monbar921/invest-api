package ru.invest.api.account.service.usecase.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.invest.api.account.service.config.PlanProperties;
import ru.invest.api.account.service.model.AccountPlanModel;
import ru.invest.api.account.service.model.CurrentSubscriptionModel;
import ru.invest.api.account.service.model.GrantPlanModel;
import ru.invest.api.account.service.model.LimitUsageModel;
import ru.invest.api.account.service.model.PaidSubscriptionModel;
import ru.invest.api.account.service.model.PlanModel;
import ru.invest.api.account.service.usecase.AccountPlanUseCase;
import ru.invest.api.common.entity.AccountAutoRenewal;
import ru.invest.api.common.entity.AccountSubscription;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.common.model.account.PlanLimit;
import ru.invest.api.common.model.account.SubscriptionSource;
import ru.invest.api.common.repository.AccountAutoRenewalRepository;
import ru.invest.api.common.repository.AccountSubscriptionRepository;
import ru.invest.api.common.repository.BondAlertRepository;
import ru.invest.api.common.repository.PortfolioPositionRepository;
import ru.invest.api.common.repository.SavedFilterRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPlanUseCaseImpl implements AccountPlanUseCase {
    private static final int MAX_COMMENT_LENGTH = 500;
    /** created_by / updated_by подписок, выданных и отозванных оплатой */
    private static final String PAYMENT_ACTOR = "payment";

    private final PlanProperties properties;
    private final ActivePlanResolver resolver;
    private final AccountSubscriptionRepository repository;
    private final AccountAutoRenewalRepository autoRenewalRepository;
    private final BondAlertRepository alertRepository;
    private final PortfolioPositionRepository positionRepository;
    private final SavedFilterRepository savedFilterRepository;
    private final DateTimeMapper dateTimeMapper;

    @Override
    @Transactional(readOnly = true)
    public AccountPlanModel getPlan(final String userId) {
        final ActivePlanResolver.ActivePlan active = resolver.resolve(userId);
        final PlanModel plan = active.plan();
        final AccountSubscription subscription = active.subscription();
        final List<LimitUsageModel> limits = Arrays.stream(PlanLimit.values())
                .map(limit -> new LimitUsageModel(limit, plan.limits().get(limit), used(userId, limit)))
                .toList();
        return new AccountPlanModel(plan, subscription == null,
                subscription == null ? null : subscription.getSource(),
                subscription == null ? null : lastDay(subscription.getValidUntil()),
                limits, resolver.plans());
    }

    @Override
    @Transactional
    public AccountPlanModel grant(final String userId, final GrantPlanModel request, final String grantedBy) {
        if (request.plan() == null || !properties.getPlans().containsKey(request.plan())) {
            throw unprocessable("Unknown plan %s, configured: %s".formatted(request.plan(),
                    String.join(", ", properties.getPlans().keySet())));
        }
        if (request.plan().equals(properties.getDefaultPlan())) {
            throw unprocessable("Plan %s is the default one: revoke the subscription instead".formatted(request.plan()));
        }
        final LocalDate today = todayInZone();
        if (request.validUntil() != null && request.validUntil().isBefore(today)) {
            throw unprocessable("validUntil must not be before today (%s)".formatted(today));
        }
        if (request.comment() != null && request.comment().length() > MAX_COMMENT_LENGTH) {
            throw unprocessable("comment must be at most %d characters".formatted(MAX_COMMENT_LENGTH));
        }
        final LocalDateTime now = dateTimeMapper.getNow();
        // подписка у пользователя одна: новая выдача заменяет действующую
        revokeActive(userId, grantedBy, now);
        cancelAutoRenewal(userId, now);
        repository.save(new AccountSubscription()
                .setUserId(userId)
                .setPlan(request.plan())
                .setSource(SubscriptionSource.MANUAL)
                .setValidFrom(now)
                .setValidUntil(request.validUntil() == null ? null : endOfDay(request.validUntil()))
                .setComment(request.comment())
                .setCreatedAt(now)
                .setCreatedBy(grantedBy));
        log.info("Plan {} granted to user {} until {} by {}", request.plan(), userId,
                request.validUntil() == null ? "forever" : request.validUntil(), grantedBy);
        return getPlan(userId);
    }

    @Override
    @Transactional
    public void revoke(final String userId, final String revokedBy) {
        final LocalDateTime now = dateTimeMapper.getNow();
        if (revokeActive(userId, revokedBy, now) > 0) {
            log.info("Plan of user {} revoked by {}", userId, revokedBy);
        }
        cancelAutoRenewal(userId, now);
    }

    @Override
    public Optional<PlanModel> findPlan(final String planId) {
        return planId != null && properties.getPlans().containsKey(planId)
                ? Optional.of(resolver.plan(planId))
                : Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentSubscriptionModel current(final String userId) {
        final ActivePlanResolver.ActivePlan active = resolver.resolve(userId);
        final AccountSubscription subscription = active.subscription();
        return new CurrentSubscriptionModel(active.plan().id(), subscription == null,
                subscription == null ? null : subscription.getValidUntil());
    }

    @Override
    @Transactional
    public PaidSubscriptionModel grantPaid(final String userId, final String planId, final Period period,
                                           final long paymentId) {
        final LocalDateTime now = dateTimeMapper.getNow();
        final ActivePlanResolver.ActivePlan active = resolver.resolve(userId);
        final AccountSubscription current = active.subscription();
        // тот же тариф со сроком - продлеваем от его последнего дня; иначе новый срок от сегодняшнего
        final LocalDate base = current != null && planId.equals(active.plan().id()) && current.getValidUntil() != null
                ? lastDay(current.getValidUntil())
                : todayInZone();
        final LocalDate lastDay = base.plus(period);
        revokeActive(userId, PAYMENT_ACTOR, now);
        final AccountSubscription subscription = repository.save(new AccountSubscription()
                .setUserId(userId)
                .setPlan(planId)
                .setSource(SubscriptionSource.PAYMENT)
                .setValidFrom(now)
                .setValidUntil(endOfDay(lastDay))
                .setPaymentId(paymentId)
                .setCreatedAt(now)
                .setCreatedBy(PAYMENT_ACTOR));
        log.info("Plan {} paid by payment {} for user {} until {} inclusive", planId, paymentId, userId, lastDay);
        return new PaidSubscriptionModel(subscription.getId(), subscription.getValidUntil(), lastDay);
    }

    @Override
    @Transactional
    public void revokePaid(final long subscriptionId) {
        final LocalDateTime now = dateTimeMapper.getNow();
        repository.findById(subscriptionId)
                .filter(subscription -> subscription.getRevokedAt() == null)
                .filter(subscription -> subscription.getValidUntil() == null
                        || subscription.getValidUntil().isAfter(now))
                .ifPresent(subscription -> {
                    repository.save(subscription.setRevokedAt(now).setUpdatedAt(now).setUpdatedBy(PAYMENT_ACTOR));
                    log.info("Paid subscription {} of user {} revoked: payment refunded", subscriptionId,
                            subscription.getUserId());
                });
    }

    /** Ручная смена тарифа отключает автопродление: иначе оно продлило бы заменённый тариф */
    private void cancelAutoRenewal(final String userId, final LocalDateTime now) {
        autoRenewalRepository.findByUserIdAndCancelledAtIsNull(userId).ifPresent(renewal -> {
            autoRenewalRepository.save(renewal.setCancelledAt(now).setCancelReason(AccountAutoRenewal.CANCEL_MANUAL));
            log.info("Auto renewal {} of user {} cancelled: plan changed manually", renewal.getId(), userId);
        });
    }

    private int revokeActive(final String userId, final String by, final LocalDateTime now) {
        final List<AccountSubscription> active = repository.findActive(userId, now);
        active.forEach(subscription -> subscription.setRevokedAt(now).setUpdatedAt(now).setUpdatedBy(by));
        repository.saveAll(active);
        return active.size();
    }

    private long used(final String userId, final PlanLimit limit) {
        return switch (limit) {
            case ALERTS -> alertRepository.countByUserId(userId);
            case PORTFOLIO_POSITIONS -> positionRepository.countByUserId(userId);
            case SAVED_FILTERS -> savedFilterRepository.countByUserId(userId);
        };
    }

    private LocalDate todayInZone() {
        return dateTimeMapper.getNow().atZone(ZoneOffset.UTC).withZoneSameInstant(properties.getZone()).toLocalDate();
    }

    /** Конец последнего дня в поясе тарифов, UTC: начало следующего дня */
    private LocalDateTime endOfDay(final LocalDate lastDay) {
        return lastDay.plusDays(1).atStartOfDay(properties.getZone())
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /** Обратное endOfDay: последний день подписки включительно */
    private LocalDate lastDay(final LocalDateTime validUntil) {
        return validUntil == null
                ? null
                : validUntil.atZone(ZoneOffset.UTC).withZoneSameInstant(properties.getZone()).toLocalDate()
                        .minusDays(1);
    }

    private static GeneralUnprocessableEntityException unprocessable(final String message) {
        return new GeneralUnprocessableEntityException(ExceptionErrorCode.PLAN_INVALID, message);
    }
}
