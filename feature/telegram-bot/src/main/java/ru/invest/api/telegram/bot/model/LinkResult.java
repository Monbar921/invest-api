package ru.invest.api.telegram.bot.model;

/**
 * Итог /start &lt;токен&gt;.
 */
public enum LinkResult {
    LINKED,
    /** токена нет, он уже использован или истёк */
    INVALID_TOKEN
}
