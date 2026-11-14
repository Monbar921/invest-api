package ru.invest.api.imports.model;

/**
 * Сделка импорта. До подтверждения: NEW, POSSIBLE_DUPLICATE, NEEDS_REVIEW - можно выбрать; DUPLICATE, UNKNOWN_BOND,
 * INVALID - нельзя. После подтверждения выбранные становятся CREATED или FAILED, невыбранные - SKIPPED.
 */
public enum ImportItemStatus {
    /** Новая сделка - выбрана по умолчанию */
    NEW(true),
    /** Похожая сделка (облигация, дата, количество) уже введена вручную - не выбрана по умолчанию */
    POSSIBLE_DUPLICATE(true),
    /** Собрана не из сделки (остаток позиции у брокера) - проверить перед импортом, не выбрана по умолчанию */
    NEEDS_REVIEW(true),
    /** Эта сделка уже импортирована - повторно не создаётся */
    DUPLICATE(false),
    /** Облигации нет в системе */
    UNKNOWN_BOND(false),
    /** Строку не разобрать или сделка не проходит проверки портфеля */
    INVALID(false),
    CREATED(false),
    FAILED(false),
    SKIPPED(false);

    private final boolean selectable;

    ImportItemStatus(final boolean selectable) {
        this.selectable = selectable;
    }

    /** Можно выбрать при подтверждении */
    public boolean isSelectable() {
        return selectable;
    }
}
