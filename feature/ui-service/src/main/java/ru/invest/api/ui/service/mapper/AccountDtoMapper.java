package ru.invest.api.ui.service.mapper;

import org.springframework.stereotype.Component;
import ru.invest.api.account.service.model.AccountPlanModel;
import ru.invest.api.account.service.model.GrantPlanModel;
import ru.invest.api.account.service.model.LimitUsageModel;
import ru.invest.api.account.service.model.PlanModel;
import ru.invest.api.dto.account.AccountPlanDto;
import ru.invest.api.dto.account.GrantPlanRequest;
import ru.invest.api.dto.account.PlanDto;
import ru.invest.api.dto.account.PlanFeatureDto;
import ru.invest.api.dto.account.PlanLimitDto;
import ru.invest.api.dto.account.PlanLimitUsageDto;

import java.util.EnumMap;
import java.util.Map;

/**
 * Тариф в DTO. Вручную: лимиты - карта с ключом-перечислением, порядок ключей - порядок PlanLimitDto.
 */
@Component
public class AccountDtoMapper {
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
                .setFeatures(plan.features().stream().map(feature -> PlanFeatureDto.valueOf(feature.name())).toList());
    }

    private static PlanLimitUsageDto toUsageDto(final LimitUsageModel usage) {
        return new PlanLimitUsageDto()
                .setLimit(PlanLimitDto.valueOf(usage.limit().name()))
                .setMax(usage.max())
                .setUsed(usage.used());
    }
}
