package ru.invest.api.budget.org.supplier.usecase;

import ru.invest.api.common.model.CurrencyModel;

public interface BudgetOrgCurrencyUseCase {
    CurrencyModel getCurrency(String base, String target);
}
