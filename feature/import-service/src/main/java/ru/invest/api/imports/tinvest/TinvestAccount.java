package ru.invest.api.imports.tinvest;

import java.time.LocalDate;

/**
 * Счёт в Т-Инвестициях, доступный токену.
 *
 * @param openedDate дата открытия (UTC); null - неизвестна
 * @param access     права токена на этот счёт
 * @param closed     счёт закрыт
 */
public record TinvestAccount(String id, String name, String type, LocalDate openedDate, Access access,
                             boolean closed) {
    public enum Access {
        /** Только чтение - то, что нужно импорту */
        READ_ONLY,
        /** Полный доступ - с таким токеном можно торговать, импорт его не принимает */
        FULL_ACCESS,
        NO_ACCESS,
        UNKNOWN
    }
}
