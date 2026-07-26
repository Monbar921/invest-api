package ru.invest.api.tinkoff.supplier.predicates;

import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static ru.invest.api.tinkoff.supplier.constants.Constants.RU_CURRENCIES;
import static ru.invest.api.tinkoff.supplier.constants.Constants.RU_PREFIX;

public interface BondCurrencyPredicates {
    Predicate<Bond> RUBBLE_CURRENCY_PREDICATE = bond -> {
        final String currency = Optional.ofNullable(bond)
                .map(Bond::getCurrency)
                .map(String::toUpperCase)
                .orElse(null);

        if (RU_CURRENCIES.contains(currency)) {
            return true;
        }

        final String nominalCurrency = Optional.ofNullable(bond)
                .map(Bond::getNominal)
                .map(MoneyValue::getCurrency)
                .map(String::toUpperCase)
                .orElse(null);

        return RU_CURRENCIES.contains(nominalCurrency);
    };

    Predicate<Bond> FOREIGN_CURRENCY_PREDICATE = bond -> !RUBBLE_CURRENCY_PREDICATE.test(bond);

    Predicate<Bond> RU_COUNTRY_PREDICATE = bond -> Optional.ofNullable(bond)
            .map(Bond::getIsin)
            .filter(isin -> isin.startsWith(RU_PREFIX))
            .isPresent();

    Predicate<Bond> OFZ_PREDICATE = bond -> Optional.ofNullable(bond)
            .filter(b -> bond.getSector().equalsIgnoreCase("GOVERNMENT"))
            .filter(b -> bond.getName().toUpperCase().startsWith("ОФЗ")
                    || bond.getName().toUpperCase().startsWith("RUSSIA")
                    || bond.getName().toUpperCase().startsWith("ОВОЗ")
            )
            .isPresent();
}
