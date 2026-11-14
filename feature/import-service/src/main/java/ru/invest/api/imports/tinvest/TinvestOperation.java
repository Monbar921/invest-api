package ru.invest.api.imports.tinvest;

import ru.invest.api.imports.model.Money;

import java.time.Instant;
import java.util.List;

/**
 * Исполненная покупка или продажа ценной бумаги на счёте.
 *
 * @param side       BUY или SELL
 * @param bond       облигация (остальные бумаги импорт пропускает)
 * @param name       название бумаги у брокера
 * @param price      цена одной бумаги (не в % номинала)
 * @param payment    сумма операции (без НКД); null - нет
 * @param aci        НКД на всю операцию; null - нет
 * @param commission комиссия брокера на всю операцию; null - нет
 * @param tradeNums  номера биржевых сделок - по ним строится ключ импорта (id операции брокер может менять)
 */
public record TinvestOperation(String id, String accountId, Side side, boolean bond, String instrumentUid,
                               String figi, String name, Instant date, long quantity, Money price, Money payment,
                               Money aci, Money commission, List<String> tradeNums) {
    public enum Side {
        BUY,
        SELL
    }
}
