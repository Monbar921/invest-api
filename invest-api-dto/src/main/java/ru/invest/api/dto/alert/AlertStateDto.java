package ru.invest.api.dto.alert;

/**
 * Состояние порога уровня: сработавший снова взводится, когда значение вернётся за порог с запасом.
 */
public enum AlertStateDto {
    ARMED,
    TRIGGERED
}
