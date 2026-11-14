package ru.invest.api.dto.portfolio.imports;

/**
 * Сделка импорта. Выбрать можно NEW (выбрана по умолчанию), POSSIBLE_DUPLICATE и NEEDS_REVIEW; после подтверждения -
 * CREATED, FAILED (причина в message) или SKIPPED (не выбрана).
 */
public enum ImportItemStatusDto {
    NEW,
    /** Похожая сделка уже введена вручную */
    POSSIBLE_DUPLICATE,
    /** Остаток позиции у брокера без покупки в истории - проверить */
    NEEDS_REVIEW,
    /** Уже импортирована */
    DUPLICATE,
    /** Облигации нет в системе */
    UNKNOWN_BOND,
    /** Не разобрана или не проходит проверки */
    INVALID,
    CREATED,
    FAILED,
    SKIPPED
}
