package ru.invest.api.dto.telegram;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.ZonedDateTime;

/**
 * Telegram пользователя: включён ли бот, привязан ли чат, отвечает ли Telegram.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class TelegramStatusDto {
    /** Бот включён в настройках invest-api; false - подключить Telegram нельзя */
    private boolean enabled;
    /** Имя бота без @ */
    private String botUsername;
    private boolean linked;
    /** @username в Telegram на момент привязки; может отсутствовать */
    private String username;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime linkedAt;
    /** Последнее обращение к Telegram успешно; null - обращений ещё не было */
    private Boolean available;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime checkedAt;
}
