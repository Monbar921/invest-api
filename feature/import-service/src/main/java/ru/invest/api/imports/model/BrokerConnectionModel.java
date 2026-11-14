package ru.invest.api.imports.model;

import ru.invest.api.imports.tinvest.TinvestAccount;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Подключение к T-Invest для импорта по токену. Сам токен не отдаётся - только последние 4 символа.
 *
 * @param enabled   импорт по токену включён (задан ключ шифрования)
 * @param connected токен сохранён
 * @param accounts  счета токена; null - не запрашивались
 */
public record BrokerConnectionModel(boolean enabled, boolean connected, String tokenHint, LocalDateTime connectedAt,
                                    LocalDateTime lastImportAt, List<TinvestAccount> accounts) {
}
