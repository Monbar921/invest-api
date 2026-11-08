package ru.invest.api.dto.telegram;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.ZonedDateTime;

/**
 * Одноразовая ссылка привязки чата.
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class TelegramLinkDto {
    /** https://t.me/&lt;bot&gt;?start=&lt;токен&gt; */
    private String url;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime expiresAt;
}
