package ru.invest.api.telegram.bot.model;

import java.time.LocalDateTime;

/**
 * Состояние Telegram для пользователя.
 *
 * @param enabled     бот включён в настройках
 * @param botUsername имя бота без @
 * @param linked      чат пользователя привязан
 * @param username    @username в Telegram на момент привязки, может отсутствовать
 * @param linkedAt    когда привязан, UTC
 * @param available   последнее обращение к Telegram успешно; null - обращений ещё не было
 * @param checkedAt   когда было последнее обращение, UTC
 */
public record TelegramStatusModel(boolean enabled,
                                  String botUsername,
                                  boolean linked,
                                  String username,
                                  LocalDateTime linkedAt,
                                  Boolean available,
                                  LocalDateTime checkedAt) {
}
