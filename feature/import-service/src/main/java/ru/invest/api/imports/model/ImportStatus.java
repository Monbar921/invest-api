package ru.invest.api.imports.model;

/** Импорт целиком */
public enum ImportStatus {
    /** Предпросмотр: ждёт подтверждения */
    PREVIEW,
    /** Подтверждён, сделки создаются */
    APPLYING,
    /** Сделки созданы - отчёт */
    APPLIED
}
