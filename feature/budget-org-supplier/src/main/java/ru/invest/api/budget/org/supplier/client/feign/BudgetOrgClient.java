package ru.invest.api.budget.org.supplier.client.feign;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.invest.api.budget.org.supplier.model.CurrencyDto;

public interface BudgetOrgClient {
    @GetMapping(value = "/{baseCurrency}/{targetCurrency}")
    CurrencyDto getCurrencyRate(@PathVariable String baseCurrency, @PathVariable String targetCurrency);
}
