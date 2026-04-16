package ru.invest.api.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.invest.api.common.exception.enums.ExceptionErrorCode;

@ResponseStatus(HttpStatus.NOT_FOUND)
@Getter
public class GeneralNotFoundEntityException extends RuntimeException {
    private final ExceptionErrorCode exceptionErrorCode;

    public GeneralNotFoundEntityException(final ExceptionErrorCode exceptionErrorCode, final String message) {
        super(message);
        this.exceptionErrorCode = exceptionErrorCode;
    }
}
