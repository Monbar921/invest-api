package ru.invest.api.tinkoff.supplier.predicates;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;

import java.util.Optional;
import java.util.function.Predicate;

import static ru.invest.api.tinkoff.supplier.constants.Constants.RU_CURRENCIES;

public interface BondModelCurrencyPredicates {
    Predicate<BondModel> RUBBLE_CURRENCY_PREDICATE = bond -> {
        final String currency = Optional.ofNullable(bond)
                .map(BondModel::getPrice)
                .map(PriceModel::getCurrent)
                .map(MoneyModel::getCurrency)
                .map(String::toUpperCase)
                .orElse(null);

        if (RU_CURRENCIES.contains(currency)) {
            return true;
        }

        final String nominalCurrency = Optional.ofNullable(bond)
                .map(BondModel::getPrice)
                .map(PriceModel::getNominal)
                .map(MoneyModel::getCurrency)
                .map(String::toUpperCase)
                .orElse(null);

        return RU_CURRENCIES.contains(nominalCurrency);
    };

    Predicate<BondModel> FOREIGN_CURRENCY_PREDICATE = bond -> !RUBBLE_CURRENCY_PREDICATE.test(bond);
}
