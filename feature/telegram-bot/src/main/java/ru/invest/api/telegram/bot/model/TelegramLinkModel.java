package ru.invest.api.telegram.bot.model;

import java.time.LocalDateTime;

/**
 * Ссылка привязки чата.
 *
 * @param url       https://t.me/&lt;bot&gt;?start=&lt;токен&gt;
 * @param expiresAt когда токен перестанет действовать, UTC
 */
public record TelegramLinkModel(String url, LocalDateTime expiresAt) {
}
