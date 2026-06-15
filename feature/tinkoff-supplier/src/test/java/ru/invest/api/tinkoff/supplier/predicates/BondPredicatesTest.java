package ru.invest.api.tinkoff.supplier.predicates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.tinkoff.piapi.contract.v1.Bond;
import ru.tinkoff.piapi.contract.v1.MoneyValue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ru.invest.api.tinkoff.supplier.predicates.BondPredicates.FOREIGN_CURRENCY_PREDICATE;
import static ru.invest.api.tinkoff.supplier.predicates.BondPredicates.ISIN_PREDICATE;

class BondPredicatesTest {

    // ─── FOREIGN_CURRENCY_PREDICATE ───────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"USD", "EUR", "CNY", "usd", "eur", "cny"})
    void foreignCurrencyPredicate_shouldAccept_whenCurrencyIsForeign(final String currency) {
        final Bond bond = Bond.newBuilder().setCurrency(currency).build();
        assertTrue(FOREIGN_CURRENCY_PREDICATE.test(bond));
    }

    @Test
    void foreignCurrencyPredicate_shouldAccept_whenNominalCurrencyIsForeign() {
        final MoneyValue nominal = MoneyValue.newBuilder().setCurrency("USD").build();
        final Bond bond = Bond.newBuilder().setCurrency("RUB").setNominal(nominal).build();
        assertTrue(FOREIGN_CURRENCY_PREDICATE.test(bond));
    }

    @Test
    void foreignCurrencyPredicate_shouldReject_whenCurrencyIsRuble() {
        final Bond bond = Bond.newBuilder().setCurrency("RUB").build();
        assertFalse(FOREIGN_CURRENCY_PREDICATE.test(bond));
    }

    @Test
    void foreignCurrencyPredicate_shouldReject_whenCurrencyIsEmpty() {
        final Bond bond = Bond.newBuilder().build();
        assertFalse(FOREIGN_CURRENCY_PREDICATE.test(bond));
    }

    @Test
    void foreignCurrencyPredicate_shouldReject_whenBothCurrenciesAreRuble() {
        final MoneyValue nominal = MoneyValue.newBuilder().setCurrency("RUB").build();
        final Bond bond = Bond.newBuilder().setCurrency("RUB").setNominal(nominal).build();
        assertFalse(FOREIGN_CURRENCY_PREDICATE.test(bond));
    }

    // ─── ISIN_PREDICATE ───────────────────────────────────────────────────────

    @Test
    void isinPredicate_shouldAccept_whenIsinStartsWithRU() {
        final Bond bond = Bond.newBuilder().setIsin("RU000A0ZZE15").build();
        assertTrue(ISIN_PREDICATE.test(bond));
    }

    @Test
    void isinPredicate_shouldReject_whenIsinStartsWithOtherPrefix() {
        final Bond bond = Bond.newBuilder().setIsin("US0378331005").build();
        assertFalse(ISIN_PREDICATE.test(bond));
    }

    @Test
    void isinPredicate_shouldReject_whenIsinIsEmpty() {
        final Bond bond = Bond.newBuilder().build();
        assertFalse(ISIN_PREDICATE.test(bond));
    }
}
