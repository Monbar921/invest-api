package ru.invest.api.dto.portfolio;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.ZonedDateTime;

/**
 * Личная ссылка на календарь выплат (.ics).
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CalendarLinkDto {
    /** Ссылка есть */
    private boolean enabled;
    /** Токен ссылки - только в ответе на создание: хранится его хеш, показать ещё раз нельзя */
    private String token;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private ZonedDateTime createdAt;
}
