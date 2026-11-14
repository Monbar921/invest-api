package ru.invest.api.imports.tinvest;

/**
 * T-Invest API отказал или не ответил. Сообщение - код и описание ответа, токена в нём нет.
 */
public class TinvestClientException extends RuntimeException {
    private final Kind kind;

    public TinvestClientException(final Kind kind, final String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public enum Kind {
        /** Токен не подошёл: неверный, отозван или истёк */
        UNAUTHENTICATED,
        /** Токену не хватает прав на запрос */
        PERMISSION_DENIED,
        /** API недоступен, лимит запросов, таймаут */
        UNAVAILABLE
    }
}
