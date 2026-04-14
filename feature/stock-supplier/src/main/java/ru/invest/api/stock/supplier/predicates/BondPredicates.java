package ru.invest.api.stock.supplier.predicates;

import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public interface BondPredicates {
    List<String> FOREIGN_CURRENCIES = List.of("EUR", "USD", "CNY");
    String RU_PREFIX = "RU";

    Predicate<Bond> FOREIGN_CURRENCY_PREDICATE = bond -> {
        final String currency = Optional.ofNullable(bond)
                .map(Bond::getCurrency)
                .map(String::toUpperCase)
                .orElse(null);

        if (FOREIGN_CURRENCIES.contains(currency)) {
            return true;
        }

        final String nominalCurrency = Optional.ofNullable(bond)
                .map(Bond::getNominal)
                .map(MoneyValue::getCurrency)
                .map(String::toUpperCase)
                .orElse(null);

        return FOREIGN_CURRENCIES.contains(nominalCurrency);
    };

    Predicate<Bond> ISIN_PREDICATE = bond -> Optional.ofNullable(bond)
            .map(Bond::getIsin)
            .filter(isin -> isin.startsWith(RU_PREFIX))
            .isPresent();
}
