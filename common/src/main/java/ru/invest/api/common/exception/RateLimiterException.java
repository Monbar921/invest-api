package ru.invest.api.common.exception;

public class RateLimiterException extends RuntimeException {
    public RateLimiterException(final String message) {
        super(message);
    }
    public RateLimiterException(final String message, final Exception cause) {
        super(message, cause);
    }

    public RateLimiterException(final Exception cause) {
        super(cause);
    }

}
