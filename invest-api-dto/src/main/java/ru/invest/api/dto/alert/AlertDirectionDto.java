package ru.invest.api.dto.alert;

public enum AlertDirectionDto {
    /** выросло до уровня / на threshold */
    UP,
    /** снизилось до уровня / на threshold */
    DOWN,
    /** изменение в любую сторону; только для изменений цены */
    ANY
}
