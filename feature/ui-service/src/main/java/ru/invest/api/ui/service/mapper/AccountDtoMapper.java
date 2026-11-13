package ru.invest.api.ui.service.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.invest.api.account.service.model.AccountPlanModel;
import ru.invest.api.account.service.model.GrantPlanModel;
import ru.invest.api.account.service.model.LimitUsageModel;
import ru.invest.api.account.service.model.PeriodTexts;
import ru.invest.api.account.service.model.PlanModel;
import ru.invest.api.common.mapper.DateTimeMapper;
import ru.invest.api.dto.account.AccountPlanDto;
import ru.invest.api.dto.account.GrantPlanRequest;
import ru.invest.api.dto.account.PaymentDto;
import ru.invest.api.dto.account.PaymentsInfoDto;
import ru.invest.api.dto.account.PlanDto;
import ru.invest.api.dto.account.PlanFeatureDto;
import ru.invest.api.dto.account.PlanLimitDto;
import ru.invest.api.dto.account.PlanLimitUsageDto;
import ru.invest.api.payment.service.model.PaymentModel;
import ru.invest.api.payment.service.model.PaymentsInfoModel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Тариф в DTO. Вручную: лимиты - карта с ключом-перечислением, порядок ключей - порядок PlanLimitDto.
 */
@Component
@RequiredArgsConstructor
public class AccountDtoMapper {
    private final DateTimeMapper dateTimeMapper;

    /** Тариф пользователя вместе с оплатой - для страницы «Тариф» */
    public AccountPlanDto toDto(final AccountPlanModel model, final PaymentsInfoModel payments) {
        return toDto(model).setPayments(toDto(payments));
    }

    public PaymentDto toDto(final PaymentModel model) {
        return new PaymentDto()
                .setId(model.id())
                .setPlan(model.plan())
                .setPlanTitle(model.planTitle())
                .setPeriod(model.period().toString())
                .setPeriodTitle(PeriodTexts.format(model.period()))
                .setAmount(model.amount())
                .setKind(model.kind().name())
                .setStatus(model.status().name())
                .setCardPan(model.cardPan())
                .setErrorMessage(model.errorMessage())
                .setConfirmationUrl(model.confirmationUrl())
                .setCreatedAt(dateTimeMapper.toZonedDateTime(model.createdAt()))
                .setPaidAt(dateTimeMapper.toZonedDateTime(model.paidAt()));
    }

    private static PaymentsInfoDto toDto(final PaymentsInfoModel model) {
        final PaymentsInfoModel.AutoRenewal renewal = model.autoRenewal();
        return new PaymentsInfoDto()
                .setEnabled(model.enabled())
                .setOfferUrl(model.offerUrl())
                .setPrivacyUrl(model.privacyUrl())
                .setAutoRenewal(renewal == null ? null : new PaymentsInfoDto.AutoRenewal()
                        .setPlan(renewal.plan())
                        .setPlanTitle(renewal.planTitle())
                        .setCardPan(renewal.cardPan())
                        .setNextChargeDate(renewal.nextChargeDate())
                        .setAmount(renewal.amount()));
    }

    public AccountPlanDto toDto(final AccountPlanModel model) {
        return new AccountPlanDto()
                .setPlan(toPlanDto(model.plan()))
                .setDefaultPlan(model.defaultPlan())
                .setSource(model.source() == null ? null : model.source().name())
                .setValidUntil(model.validUntil())
                .setLimits(model.limits().stream().map(AccountDtoMapper::toUsageDto).toList())
                .setPlans(model.plans().stream().map(this::toPlanDto).toList());
    }

    public GrantPlanModel toModel(final GrantPlanRequest request) {
        return new GrantPlanModel(request.getPlan(), request.getValidUntil(), request.getComment());
    }

    private PlanDto toPlanDto(final PlanModel plan) {
        final Map<PlanLimitDto, Integer> limits = new EnumMap<>(PlanLimitDto.class);
        plan.limits().forEach((limit, max) -> limits.put(PlanLimitDto.valueOf(limit.name()), max));
        return new PlanDto()
                .setId(plan.id())
                .setTitle(plan.title())
                .setDescription(plan.description())
                .setPaid(plan.paid())
                .setLimits(limits)
                .setFeatures(plan.features().stream().map(feature -> PlanFeatureDto.valueOf(feature.name())).toList())
                .setPrice(plan.price())
                .setPeriod(plan.period() == null ? null : plan.period().toString())
                .setPeriodTitle(PeriodTexts.format(plan.period()))
                .setPurchasable(plan.purchasable());
    }

    private static PlanLimitUsageDto toUsageDto(final LimitUsageModel usage) {
        return new PlanLimitUsageDto()
                .setLimit(PlanLimitDto.valueOf(usage.limit().name()))
                .setMax(usage.max())
                .setUsed(usage.used());
    }
}
