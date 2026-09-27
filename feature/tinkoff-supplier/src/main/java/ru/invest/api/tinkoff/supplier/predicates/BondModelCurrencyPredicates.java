package ru.invest.api.tinkoff.supplier.predicates;

import ru.invest.api.common.model.BondModel;
import ru.invest.api.common.model.MoneyModel;
import ru.invest.api.common.model.PriceModel;

import java.util.Optional;
import java.util.function.Predicate;

import static ru.invest.api.tinkoff.supplier.constants.Constants.RU_CURRENCIES;

public interface BondModelCurrencyPredicates {
    // Set.of(...).contains(null) бросает NPE, а у облигации без синхронизированной цены валюты нет
    Predicate<BondModel> RUBBLE_CURRENCY_PREDICATE = bond -> {
        final boolean isRubbleCurrentPrice = Optional.ofNullable(bond)
                .map(BondModel::getPrice)
                .map(PriceModel::getCurrent)
                .map(MoneyModel::getCurrency)
                .map(String::toUpperCase)
                .filter(RU_CURRENCIES::contains)
                .isPresent();

        if (isRubbleCurrentPrice) {
            return true;
        }

        return Optional.ofNullable(bond)
                .map(BondModel::getPrice)
                .map(PriceModel::getNominal)
                .map(MoneyModel::getCurrency)
                .map(String::toUpperCase)
                .filter(RU_CURRENCIES::contains)
                .isPresent();
    };

    Predicate<BondModel> FOREIGN_CURRENCY_PREDICATE = bond -> !RUBBLE_CURRENCY_PREDICATE.test(bond);
}
