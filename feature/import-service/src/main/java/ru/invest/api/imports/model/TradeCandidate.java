package ru.invest.api.imports.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Сделка, как её дал источник, до сопоставления с облигациями системы и проверок.
 *
 * @param lineNo        номер строки файла или операции - порядок в отчёте
 * @param importKey     ключ сделки у источника: тот же при повторном импорте того же отчёта
 * @param instrumentUid uid инструмента T-Invest (как у облигаций в системе); null - нет
 * @param pricePercent  цена в % номинала (отчёты); null - цена в деньгах в price
 * @param price         цена одной облигации в деньгах (T-Invest); null - цена в pricePercent
 * @param aci           НКД на всю сделку; валюта null - валюта номинала
 * @param commission    комиссия на всю сделку; валюта null - валюта номинала
 * @param error         строку не разобрать - почему; тогда остальные поля могут быть пустыми
 * @param reviewNote    сделка собрана не из сделки брокера - что проверить
 */
public record TradeCandidate(int lineNo, ImportKind kind, String importKey, LocalDate date, String instrumentUid,
                             String isin, String ticker, String figi, String name, Integer quantity,
                             BigDecimal pricePercent, Money price, Money aci, Money commission, String broker,
                             String error, String reviewNote) {
    /** Строка, которую не разобрать */
    public static TradeCandidate invalid(final int lineNo, final String error) {
        return new TradeCandidate(lineNo, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, error, null);
    }
}
