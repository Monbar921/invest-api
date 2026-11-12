package ru.invest.api.portfolio.service.model;

import java.time.LocalDateTime;

/**
 * Личная ссылка на календарь выплат.
 *
 * @param token     токен ссылки; отдаётся только при создании - в БД хранится его хеш
 * @param createdAt UTC; null - ссылки нет
 */
public record CalendarLinkModel(String token, LocalDateTime createdAt) {
}
