package ru.invest.api.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;

/**
 * Запрос без пользователя: эндпоинты пользователя вызываются только через gateway, он ставит X-User-Id.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
@Getter
public class GeneralUnauthorizedException extends RuntimeException {
    private final ExceptionErrorCode exceptionErrorCode;

    public GeneralUnauthorizedException(final ExceptionErrorCode exceptionErrorCode, final String message) {
        super(message);
        this.exceptionErrorCode = exceptionErrorCode;
    }
}
