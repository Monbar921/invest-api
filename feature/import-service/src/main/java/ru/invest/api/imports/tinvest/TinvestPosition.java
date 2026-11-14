package ru.invest.api.imports.tinvest;

import ru.invest.api.imports.model.Money;

import java.math.BigDecimal;

/**
 * Облигация на счёте сейчас.
 *
 * @param averagePrice средняя цена позиции у брокера, за одну облигацию
 */
public record TinvestPosition(String instrumentUid, String figi, String ticker, BigDecimal quantity,
                              Money averagePrice) {
}
