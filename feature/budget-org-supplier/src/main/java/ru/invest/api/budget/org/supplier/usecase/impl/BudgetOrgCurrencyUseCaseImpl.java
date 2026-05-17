package ru.invest.api.budget.org.supplier.usecase.impl;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import ru.invest.api.budget.org.supplier.client.feign.BudgetOrgClient;
import ru.invest.api.budget.org.supplier.mapper.BudgetOrgCurrencyMapper;
import ru.invest.api.budget.org.supplier.usecase.BudgetOrgCurrencyUseCase;
import ru.invest.api.common.exception.GeneralUnprocessableEntityException;
import ru.invest.api.common.model.CurrencyModel;

import static ru.invest.api.common.constants.CacheConstants.BUDGET_ORG_CACHE_MANAGER;
import static ru.invest.api.common.constants.CacheConstants.BUDGET_ORG_CACHE_NAME;
import static ru.invest.api.common.exception.enums.ExceptionErrorCode.EMPTY_CURRENCY;

@Component
@RequiredArgsConstructor
public class BudgetOrgCurrencyUseCaseImpl implements BudgetOrgCurrencyUseCase {
    private final BudgetOrgCurrencyMapper currencyMapper;

    private final BudgetOrgClient budgetOrgClient;

    @Override
    @Cacheable(value = BUDGET_ORG_CACHE_NAME, cacheManager = BUDGET_ORG_CACHE_MANAGER)
    public CurrencyModel getCurrency(final String base, final String target) {
        if (StringUtils.isBlank(base) || StringUtils.isBlank(target)) {
            throw new GeneralUnprocessableEntityException(EMPTY_CURRENCY, "Provide currency codes");
        }

        return currencyMapper.toModel(
                budgetOrgClient.getCurrencyRate(base, target)
        );
    }
}
